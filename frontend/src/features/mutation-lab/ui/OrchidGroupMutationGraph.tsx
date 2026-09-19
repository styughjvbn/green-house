"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Background,
  Controls,
  Handle,
  MarkerType,
  MiniMap,
  Position,
  ReactFlow,
  type Edge,
  type Node,
  type NodeProps,
} from "@xyflow/react";
import { GitFork, MapPin } from "lucide-react";
import { useMemo } from "react";
import {
  buildReadableMutationGraph,
  layoutMutationGraph,
  type MutationGraphViewEdge,
} from "../lib/orchidGroupMutationGraph";
import { DOMAIN_LABELS, TYPE_LABELS } from "../lib/mutationLabLabels";
import { mutationGraphQueryOptions } from "../model/mutationLabQueryOptions";
import type { MutationGraph, MutationGraphNode } from "../model/types";

const GRAPH_DEPTH = 2;
const GRAPH_NODE_LIMIT = 120;

type FlowNodeData = Omit<Partial<MutationGraphNode>, "nodeType"> &
  Record<string, unknown> & {
    id: string;
    nodeType: MutationGraphNode["nodeType"] | "JUNCTION";
    rootOrchidGroupId: number;
  };
type MutationFlowNode = Node<FlowNodeData, "state" | "mutation" | "junction">;

const nodeTypes = {
  state: StateNode,
  mutation: MutationNode,
  junction: ResultJunctionNode,
};

export function OrchidGroupMutationGraph({
  orchidGroupId,
}: {
  orchidGroupId: number;
}) {
  const query = useQuery(
    mutationGraphQueryOptions(orchidGroupId, GRAPH_DEPTH, GRAPH_NODE_LIMIT),
  );
  const flow = useMemo(
    () => (query.data ? toReactFlow(query.data) : { nodes: [], edges: [] }),
    [query.data],
  );

  return (
    <section className="overflow-hidden rounded-md border border-[#cadaca] bg-white shadow-sm">
      <header className="flex flex-wrap items-start justify-between gap-3 border-b border-[#e1e8df] bg-[#f4f8f3] px-4 py-3">
        <div>
          <div className="flex items-center gap-2 text-[#1d6f3a]">
            <GitFork className="h-5 w-5" aria-hidden="true" />
            <h2 className="font-bold">
              난 묶음 #{orchidGroupId} Mutation·계보 그래프
            </h2>
          </div>
          <p className="mt-1 text-xs text-[#637067]">
            상태 revision 사이에 Mutation을 배치하고, 구조 변경의 분기·합류와
            보정 관계를 함께 표시합니다.
          </p>
        </div>
        <span className="rounded-full bg-white px-3 py-1 text-xs font-bold text-[#496052] shadow-sm">
          {query.data?.nodes.length ?? 0}개 node ·{" "}
          {query.data?.edges.length ?? 0}개 edge · 계보 깊이 {GRAPH_DEPTH}
        </span>
      </header>

      {query.isPending ? (
        <GraphMessage>통합 그래프를 불러오는 중입니다.</GraphMessage>
      ) : query.isError ? (
        <GraphMessage tone="error">
          통합 그래프를 불러오지 못했습니다. {query.error.message}
        </GraphMessage>
      ) : !query.data?.nodes.length ? (
        <GraphMessage>이 난 묶음의 Mutation Entry가 없습니다.</GraphMessage>
      ) : (
        <div>
          {query.data.truncated ? (
            <p className="m-3 rounded-md bg-[#fff6da] px-3 py-2 text-xs font-semibold text-[#765711]">
              그래프가 {query.data.maxNodes}개 노드 제한에 도달했습니다. 선택한
              묶음과 가까운 이력을 우선 표시합니다.
            </p>
          ) : null}
          <GraphLegend />
          <div
            className="h-[38rem] min-w-0 bg-[#f8faf7]"
            aria-label="난 묶음 Mutation과 계보 node-edge 그래프"
          >
            <ReactFlow
              nodes={flow.nodes}
              edges={flow.edges}
              nodeTypes={nodeTypes}
              nodesDraggable={false}
              nodesConnectable={false}
              elementsSelectable
              fitView
              fitViewOptions={{ padding: 0.14, maxZoom: 1 }}
              minZoom={0.08}
              maxZoom={1.6}
              proOptions={{ hideAttribution: true }}
            >
              <Background color="#cbd7ca" gap={20} size={1} />
              <MiniMap
                pannable
                zoomable
                nodeColor={(node) => miniMapColor(node.data as FlowNodeData)}
                maskColor="rgba(244, 248, 243, 0.72)"
              />
              <Controls showInteractive={false} />
            </ReactFlow>
          </div>
        </div>
      )}
    </section>
  );
}

function StateNode({ data }: NodeProps<MutationFlowNode>) {
  const state = data.state;
  const root = data.orchidGroupId === data.rootOrchidGroupId;
  return (
    <article
      className={`w-[250px] overflow-hidden rounded-lg border bg-white text-xs shadow-md ${
        root ? "border-[#2f8d4b] ring-2 ring-[#cde8d3]" : "border-[#cad8c9]"
      }`}
    >
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
      <div className={`h-1.5 ${root ? "bg-[#159447]" : "bg-[#4b83b6]"}`} />
      <div className="p-3">
        <div className="flex items-start justify-between gap-2">
          <strong className="text-sm text-[#213429]">
            난 묶음 #{data.orchidGroupId}
          </strong>
          <span className="shrink-0 rounded bg-[#eef4ed] px-1.5 py-0.5 text-[10px] font-bold text-[#526558]">
            r{data.stateRevision}
          </span>
        </div>
        {state ? (
          <>
            <p className="mt-1 truncate text-[#657269]">
              {state.varietyName ?? "품종 없음"} · {state.status ?? "상태 없음"}
            </p>
            <div className="mt-3 grid grid-cols-3 gap-1.5">
              <Metric label="전체" value={state.quantity} />
              <Metric label="예약" value={state.reservedQuantity} />
              <Metric
                label="가용"
                value={availableQuantity(
                  state.quantity,
                  state.reservedQuantity,
                )}
              />
            </div>
            <p className="mt-3 flex items-center gap-1 border-t border-[#e6ebe4] pt-2 text-[#607067]">
              <MapPin className="h-3 w-3" aria-hidden="true" />
              구역 {state.bedZoneId ?? "-"} · 위치 {state.startPosition ?? "?"}–
              {state.endPosition ?? "?"}
            </p>
          </>
        ) : (
          <p className="mt-3 rounded bg-[#fff0ee] px-2 py-3 text-center font-bold text-[#a44b42]">
            삭제된 terminal 상태
          </p>
        )}
      </div>
    </article>
  );
}

function MutationNode({ data }: NodeProps<MutationFlowNode>) {
  return (
    <article className="w-[220px] overflow-hidden rounded-lg border border-[#d7c9a6] bg-[#fffdf7] text-xs shadow-md">
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
      <Handle
        id="result-out"
        type="source"
        position={Position.Right}
        style={{ top: "78%", background: "#2677a8" }}
      />
      <Handle id="relation-in" type="target" position={Position.Top} />
      <Handle id="relation-out" type="source" position={Position.Bottom} />
      <div
        className="h-1.5"
        style={{ backgroundColor: mutationColor(data.mutationType) }}
      />
      <div className="p-3">
        <div className="flex items-start justify-between gap-2">
          <strong className="text-sm text-[#3d321e]">
            {TYPE_LABELS[data.mutationType ?? ""] ?? data.mutationType}
          </strong>
          <span className="shrink-0 rounded bg-[#f5eddc] px-1.5 py-0.5 text-[10px] font-bold text-[#6c5a34]">
            M#{data.mutationId}
          </span>
        </div>
        <p className="mt-1 text-[#766c58]">
          {data.effectiveBusinessDate ?? "-"} ·{" "}
          {DOMAIN_LABELS[data.sourceDomain ?? ""] ?? data.sourceDomain}
        </p>
        <p className="mt-3 truncate border-t border-[#eee5d4] pt-2 text-[#6e624e]">
          {data.sourceType} #{data.sourceReferenceId}
        </p>
      </div>
    </article>
  );
}

function ResultJunctionNode() {
  return (
    <div className="relative h-[22px] w-[22px] rotate-45 rounded-sm border-2 border-[#2677a8] bg-[#e8f4fa] shadow-sm">
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
      <span className="absolute top-7 left-1/2 -translate-x-1/2 -rotate-45 text-[10px] font-bold whitespace-nowrap text-[#2677a8]">
        결과 분기
      </span>
    </div>
  );
}

function GraphLegend() {
  return (
    <div className="flex flex-wrap gap-x-4 gap-y-1 border-b border-[#e7ece5] px-4 py-2 text-[11px] font-semibold text-[#647168]">
      <LegendMark color="#159447" label="선택 난 묶음 상태" />
      <LegendMark color="#4b83b6" label="연결 난 묶음 상태" />
      <LegendMark color="#d18412" label="Mutation" />
      <LegendMark color="#2677a8" label="신규 결과 분기·합류" />
      <LegendMark color="#d18412" label="보정·보상 관계" dashed />
    </div>
  );
}

function LegendMark({
  color,
  label,
  dashed = false,
}: {
  color: string;
  label: string;
  dashed?: boolean;
}) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        className="block h-0 w-6 border-t-2"
        style={{ borderColor: color, borderStyle: dashed ? "dashed" : "solid" }}
      />
      {label}
    </span>
  );
}

function Metric({ label, value }: { label: string; value?: number | null }) {
  return (
    <div className="rounded bg-[#f4f7f3] px-2 py-1.5 text-center">
      <span className="block text-[10px] text-[#77837a]">{label}</span>
      <strong className="text-[#304437]">
        {value == null ? "-" : `${value}분`}
      </strong>
    </div>
  );
}

function toReactFlow(graph: MutationGraph) {
  const readableGraph = buildReadableMutationGraph(graph.nodes, graph.edges);
  const layoutNodes = layoutMutationGraph(
    readableGraph.nodes,
    readableGraph.edges,
  );
  const nodes: MutationFlowNode[] = layoutNodes.map((node) => ({
    id: node.id,
    type:
      node.nodeType === "STATE"
        ? "state"
        : node.nodeType === "MUTATION"
          ? "mutation"
          : "junction",
    position: node.position,
    data: { ...node, rootOrchidGroupId: graph.rootOrchidGroupId },
  }));
  const edges: Edge[] = readableGraph.edges.map(toReactFlowEdge);
  return { nodes, edges };
}

function toReactFlowEdge(edge: MutationGraphViewEdge): Edge {
  const relation = edge.edgeType === "MUTATION_RELATION";
  const resultFlow = ["RESULT_BUNDLE", "RESULT_BRANCH"].includes(edge.edgeType);
  const color = relation ? "#d18412" : resultFlow ? "#2677a8" : "#6d826f";
  return {
    id: edge.id,
    source: edge.sourceNodeId,
    target: edge.targetNodeId,
    sourceHandle: relation
      ? "relation-out"
      : edge.edgeType === "RESULT_BUNDLE"
        ? "result-out"
        : undefined,
    targetHandle: relation ? "relation-in" : undefined,
    type: relation || resultFlow ? "smoothstep" : "default",
    label: edge.edgeType === "RESULT_BUNDLE" ? undefined : edgeLabel(edge),
    animated: relation,
    markerEnd:
      edge.edgeType === "RESULT_BUNDLE"
        ? undefined
        : { type: MarkerType.ArrowClosed, color },
    style: {
      stroke: color,
      strokeWidth: resultFlow ? 2.6 : 2,
      strokeDasharray: relation ? "6 4" : undefined,
    },
    labelStyle: { fill: color, fontSize: 11, fontWeight: 700 },
    labelBgStyle: { fill: "#fffdf7" },
    labelBgPadding: [5, 3] as [number, number],
    labelBgBorderRadius: 4,
  };
}

function edgeLabel(edge: MutationGraphViewEdge) {
  if (edge.mutationRelationType === "CORRECTS") return "보정";
  if (edge.mutationRelationType === "COMPENSATES") return "보상";
  if (edge.mutationRelationType === "SUPERSEDES") return "대체";
  if (edge.lineageRelationType) return lineageLabel(edge.lineageRelationType);
  if (edge.entryRole === "SOURCE") return "원본";
  if (edge.entryRole === "RESULT") return "결과";
  return undefined;
}

function lineageLabel(type: string) {
  const labels: Record<string, string> = {
    CREATED_FROM_INBOUND: "입고 생성",
    REPOTTED_TO: "분갈이",
    SPLIT_TO: "분주",
    MERGED_TO: "합식",
    MOVED_TO: "이동 결과",
    POTTED_TO: "포트 작업",
    CORRECTED_TO: "계보 보정",
  };
  return labels[type] ?? type;
}

function availableQuantity(quantity?: number | null, reserved?: number | null) {
  return quantity == null || reserved == null ? null : quantity - reserved;
}

function miniMapColor(data: FlowNodeData) {
  if (data.nodeType === "JUNCTION") return "#2677a8";
  if (data.nodeType === "MUTATION") return mutationColor(data.mutationType);
  return data.orchidGroupId === data.rootOrchidGroupId ? "#159447" : "#4b83b6";
}

function mutationColor(type?: string | null) {
  if (["CORRECTION", "COMPENSATION", "RESTORE_OUTBOUND"].includes(type ?? "")) {
    return "#e69a20";
  }
  if (["DELETE", "DISCARD", "CANCEL_CREATION"].includes(type ?? "")) {
    return "#dc5b4d";
  }
  if (["CREATE", "BASELINE_IMPORT"].includes(type ?? "")) return "#7957b5";
  return "#d18412";
}

function GraphMessage({
  children,
  tone = "default",
}: {
  children: React.ReactNode;
  tone?: "default" | "error";
}) {
  return (
    <div
      className={`px-4 py-14 text-center text-sm font-semibold ${
        tone === "error" ? "text-[#a33b32]" : "text-[#69756c]"
      }`}
    >
      {children}
    </div>
  );
}
