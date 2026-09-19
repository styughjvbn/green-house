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
  buildOrchidGroupMutationFlow,
  type OrchidGroupMutationGraphPoint,
} from "../lib/orchidGroupMutationGraph";
import { DOMAIN_LABELS, TYPE_LABELS } from "../lib/mutationLabLabels";
import { mutationLabQueryOptions } from "../model/mutationLabQueryOptions";

const GRAPH_LIMIT = 100;
const NODE_WIDTH = 250;
const NODE_GAP = 90;

type MutationNodeData = OrchidGroupMutationGraphPoint & Record<string, unknown>;
type MutationFlowNode = Node<MutationNodeData, "mutation">;

const nodeTypes = { mutation: MutationNode };

export function OrchidGroupMutationGraph({
  orchidGroupId,
}: {
  orchidGroupId: number;
}) {
  const query = useQuery(
    mutationLabQueryOptions({
      orchidGroupId,
      mutationType: null,
      sourceDomain: null,
      page: 0,
      size: GRAPH_LIMIT,
    }),
  );
  const graph = useMemo(
    () =>
      buildOrchidGroupMutationFlow(query.data?.content ?? [], orchidGroupId),
    [query.data?.content, orchidGroupId],
  );
  const flow = useMemo(() => toReactFlow(graph), [graph]);
  const truncated = (query.data?.totalElements ?? 0) > GRAPH_LIMIT;

  return (
    <section className="overflow-hidden rounded-md border border-[#cadaca] bg-white shadow-sm">
      <header className="flex flex-wrap items-start justify-between gap-3 border-b border-[#e1e8df] bg-[#f4f8f3] px-4 py-3">
        <div>
          <div className="flex items-center gap-2 text-[#1d6f3a]">
            <GitFork className="h-5 w-5" aria-hidden="true" />
            <h2 className="font-bold">
              난 묶음 #{orchidGroupId} Mutation 그래프
            </h2>
          </div>
          <p className="mt-1 text-xs text-[#637067]">
            실선은 revision 흐름, 점선은 보정·보상 관계입니다. 이동·확대와
            축소가 가능합니다.
          </p>
        </div>
        <span className="rounded-full bg-white px-3 py-1 text-xs font-bold text-[#496052] shadow-sm">
          {graph.points.length}개 node · {graph.edges.length}개 edge
        </span>
      </header>

      {query.isPending ? (
        <GraphMessage>Mutation 그래프를 불러오는 중입니다.</GraphMessage>
      ) : query.isError ? (
        <GraphMessage tone="error">
          Mutation 그래프를 불러오지 못했습니다. {query.error.message}
        </GraphMessage>
      ) : graph.points.length === 0 ? (
        <GraphMessage>이 난 묶음의 Mutation Entry가 없습니다.</GraphMessage>
      ) : (
        <div>
          {truncated ? (
            <p className="m-3 rounded-md bg-[#fff6da] px-3 py-2 text-xs font-semibold text-[#765711]">
              전체 {query.data?.totalElements}개 중 최신 {GRAPH_LIMIT}개
              Mutation만 표시합니다.
            </p>
          ) : null}
          <div
            className="h-[34rem] min-w-0 bg-[#f8faf7]"
            aria-label="난 묶음 Mutation node-edge 그래프"
          >
            <ReactFlow
              nodes={flow.nodes}
              edges={flow.edges}
              nodeTypes={nodeTypes}
              nodesDraggable={false}
              nodesConnectable={false}
              elementsSelectable
              fitView
              fitViewOptions={{ padding: 0.18, maxZoom: 1 }}
              minZoom={0.15}
              maxZoom={1.5}
              proOptions={{ hideAttribution: true }}
            >
              <Background color="#cbd7ca" gap={20} size={1} />
              <MiniMap
                pannable
                zoomable
                nodeColor={(node) =>
                  nodeColor(node.data.mutationType as string)
                }
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

function MutationNode({ data }: NodeProps<MutationFlowNode>) {
  return (
    <article className="w-[250px] overflow-hidden rounded-lg border border-[#cad8c9] bg-white text-xs shadow-md">
      <Handle id="history-in" type="target" position={Position.Left} />
      <Handle id="history-out" type="source" position={Position.Right} />
      <Handle id="relation-in" type="target" position={Position.Top} />
      <Handle id="relation-out" type="source" position={Position.Bottom} />
      <div
        className="h-1.5"
        style={{ backgroundColor: nodeColor(data.mutationType) }}
      />
      <div className="p-3">
        <div className="flex items-start justify-between gap-2">
          <strong className="text-sm text-[#213429]">
            r{data.revision} · {TYPE_LABELS[data.mutationType]}
          </strong>
          <span className="shrink-0 rounded bg-[#eef4ed] px-1.5 py-0.5 text-[10px] font-bold text-[#526558]">
            #{data.mutationId}
          </span>
        </div>
        <p className="mt-1 text-[#6a766d]">
          {data.effectiveBusinessDate} · {DOMAIN_LABELS[data.sourceDomain]}
        </p>
        <div className="mt-3 grid grid-cols-3 gap-1.5">
          <Metric label="전체" value={data.quantity} />
          <Metric label="예약" value={data.reservedQuantity} />
          <Metric label="가용" value={data.availableQuantity} />
        </div>
        <p className="mt-3 flex items-center gap-1 border-t border-[#e6ebe4] pt-2 text-[#607067]">
          <MapPin className="h-3 w-3" aria-hidden="true" />
          구역 {data.bedZoneId ?? "-"} · {data.status ?? "상태 없음"}
        </p>
      </div>
    </article>
  );
}

function Metric({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="rounded bg-[#f4f7f3] px-2 py-1.5 text-center">
      <span className="block text-[10px] text-[#77837a]">{label}</span>
      <strong className="text-[#304437]">
        {value == null ? "-" : `${value}분`}
      </strong>
    </div>
  );
}

function toReactFlow(graph: ReturnType<typeof buildOrchidGroupMutationFlow>) {
  const nodes: MutationFlowNode[] = graph.points.map((point, index) => ({
    id: mutationNodeId(point.mutationId),
    type: "mutation",
    position: { x: index * (NODE_WIDTH + NODE_GAP), y: 100 },
    data: point,
  }));
  const edges: Edge[] = graph.edges.map((edge) => {
    const relation = edge.kind === "relation";
    return {
      id: edge.id,
      source: mutationNodeId(edge.sourceMutationId),
      target: mutationNodeId(edge.targetMutationId),
      sourceHandle: relation ? "relation-out" : "history-out",
      targetHandle: relation ? "relation-in" : "history-in",
      type: relation ? "smoothstep" : "default",
      label: relation ? relationLabel(edge.relationType) : undefined,
      animated: relation,
      markerEnd: {
        type: MarkerType.ArrowClosed,
        color: relation ? "#d18412" : "#6d826f",
      },
      style: relation
        ? { stroke: "#d18412", strokeWidth: 2, strokeDasharray: "6 4" }
        : { stroke: "#6d826f", strokeWidth: 2 },
      labelStyle: relation
        ? { fill: "#8a5a0c", fontSize: 11, fontWeight: 700 }
        : undefined,
      labelBgStyle: relation ? { fill: "#fff8e7" } : undefined,
      labelBgPadding: relation ? ([5, 3] as [number, number]) : undefined,
      labelBgBorderRadius: relation ? 4 : undefined,
    };
  });
  return { nodes, edges };
}

function mutationNodeId(mutationId: number) {
  return `mutation-${mutationId}`;
}

function relationLabel(type?: string) {
  if (type === "CORRECTS") return "보정";
  if (type === "COMPENSATES") return "보상";
  if (type === "SUPERSEDES") return "대체";
  return type ?? "관계";
}

function nodeColor(type: string) {
  if (["CORRECTION", "COMPENSATION", "RESTORE_OUTBOUND"].includes(type)) {
    return "#e69a20";
  }
  if (["DELETE", "DISCARD", "CANCEL_CREATION"].includes(type)) {
    return "#dc5b4d";
  }
  if (["CREATE", "BASELINE_IMPORT"].includes(type)) return "#3182ce";
  return "#159447";
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
