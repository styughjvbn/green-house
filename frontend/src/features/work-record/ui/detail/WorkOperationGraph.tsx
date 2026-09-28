"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Background,
  Controls,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
  type Edge,
  type Node,
  type NodeProps,
} from "@xyflow/react";
import { GitFork } from "lucide-react";
import { useMemo, useState } from "react";
import {
  bundleMutationResults,
  layoutLineageGraph,
} from "@/shared/lib/graph/lineageGraphLayout";
import type {
  WorkOperationGraph as WorkGraph,
  WorkOperationGraphDetail,
  WorkOperationGraphNode,
} from "../../model/types";
import { workOperationGraphQueryOptions } from "../../model/workRecordQueryOptions";

type FlowData = WorkOperationGraphNode & Record<string, unknown>;
type FlowNode = Node<FlowData>;

const nodeTypes = {
  origin: OriginNode,
  batch: BatchNode,
  work: WorkNode,
  mutation: MutationNode,
  state: StateNode,
  junction: JunctionNode,
};

export function WorkOperationGraph({
  workOperationId,
  onSelectOperation,
}: {
  workOperationId: number;
  onSelectOperation: (id: number) => void;
}) {
  const [detail, setDetail] = useState<WorkOperationGraphDetail>("WORK");
  const [depth, setDepth] = useState(1);
  const query = useQuery(
    workOperationGraphQueryOptions(workOperationId, detail, depth),
  );
  const flow = useMemo(
    () => (query.data ? toFlow(query.data) : { nodes: [], edges: [] }),
    [query.data],
  );

  return (
    <section className="mt-4 overflow-hidden rounded-md border border-[#d7e1d5] bg-white">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-[#e3e9e1] bg-[#f5f8f4] px-3 py-2">
        <div className="flex items-center gap-2 font-bold text-[#285b37]">
          <GitFork className="h-4 w-4" aria-hidden="true" />
          작업 관계 그래프
        </div>
        <div className="flex flex-wrap items-center gap-1">
          {(["WORK", "MUTATION", "LINEAGE"] as const).map((value) => (
            <button
              className={`rounded px-2 py-1 text-xs font-semibold ${
                detail === value
                  ? "bg-[#187a3d] text-white"
                  : "bg-white text-[#4e6254]"
              }`}
              key={value}
              type="button"
              onClick={() => setDetail(value)}
            >
              {value === "WORK"
                ? "작업 관계"
                : value === "MUTATION"
                  ? "상태 변화"
                  : "계보 확장"}
            </button>
          ))}
          {detail === "LINEAGE" ? (
            <select
              aria-label="계보 깊이"
              className="ml-1 rounded border border-[#ccd8ca] bg-white px-2 py-1 text-xs"
              value={depth}
              onChange={(event) => setDepth(Number(event.target.value))}
            >
              {[1, 2, 3].map((value) => (
                <option key={value} value={value}>
                  깊이 {value}
                </option>
              ))}
            </select>
          ) : null}
        </div>
      </header>
      {query.isPending ? (
        <Message>그래프를 불러오는 중입니다.</Message>
      ) : query.isError ? (
        <Message>그래프를 불러오지 못했습니다.</Message>
      ) : !flow.nodes.length ? (
        <Message>표시할 관계가 없습니다.</Message>
      ) : (
        <>
          {query.data?.truncated ? (
            <p className="m-2 rounded bg-[#fff4cf] px-3 py-2 text-xs text-[#765711]">
              가까운 관계부터 {query.data.maxNodes}개 노드만 표시합니다.
            </p>
          ) : null}
          <div className="h-[30rem] bg-[#f8faf7]">
            <ReactFlow
              nodes={flow.nodes}
              edges={flow.edges}
              nodeTypes={nodeTypes}
              nodesDraggable={false}
              nodesConnectable={false}
              fitView
              minZoom={0.12}
              maxZoom={1.5}
              proOptions={{ hideAttribution: true }}
              onNodeClick={(_, node) => {
                const data = node.data as FlowData;
                if (
                  data.nodeType === "WORK_OPERATION" &&
                  data.workOperationId
                ) {
                  onSelectOperation(data.workOperationId);
                }
                if (
                  data.nodeType === "ORIGIN" &&
                  data.originType === "INBOUND" &&
                  data.originReferenceId
                ) {
                  window.location.href = `/inventory/inbound?inboundId=${data.originReferenceId}`;
                }
              }}
            >
              <Background color="#ced8cc" gap={20} size={1} />
              <Controls showInteractive={false} />
            </ReactFlow>
          </div>
        </>
      )}
    </section>
  );
}

function toFlow(graph: WorkGraph) {
  const readable = bundleMutationResults(graph.nodes, graph.edges);
  const laidOut = layoutLineageGraph(readable.nodes, readable.edges, {
    ORIGIN: { width: 210, height: 90 },
    CREATION_BATCH: { width: 220, height: 90 },
    WORK_OPERATION: { width: 250, height: 150 },
    MUTATION: { width: 220, height: 100 },
    STATE: { width: 240, height: 145 },
    JUNCTION: { width: 22, height: 22 },
  });
  const nodes: FlowNode[] = laidOut.map((node) => ({
    id: node.id,
    type:
      node.nodeType === "ORIGIN"
        ? "origin"
        : node.nodeType === "CREATION_BATCH"
          ? "batch"
          : node.nodeType === "WORK_OPERATION"
            ? "work"
            : node.nodeType === "MUTATION"
              ? "mutation"
              : node.nodeType === "STATE"
                ? "state"
                : "junction",
    position: node.position,
    data: node as FlowData,
  }));
  const edges: Edge[] = readable.edges.map((edge) => {
    const relation = edge.edgeType === "MUTATION_RELATION";
    const label = edgeLabel(edge.edgeType, edge.relationType);
    return {
      id: edge.id,
      source: edge.sourceNodeId,
      target: edge.targetNodeId,
      type: relation ? "smoothstep" : "default",
      label,
      animated: relation,
      markerEnd: { type: MarkerType.ArrowClosed, color: "#6d826f" },
      style: { stroke: relation ? "#d18412" : "#6d826f", strokeWidth: 2 },
      labelStyle: { fill: "#4f6255", fontSize: 11, fontWeight: 700 },
      labelBgStyle: { fill: "#fff" },
    };
  });
  return { nodes, edges };
}

function OriginNode({ data }: NodeProps<FlowNode>) {
  const label =
    data.originType === "INBOUND"
      ? "입고 관리"
      : data.originType === "SYSTEM"
        ? "시스템 자동 생성"
        : "작업 관리에서 등록";
  return (
    <Card tone="origin">
      <Handle type="source" position={Position.Right} />
      <strong>{label}</strong>
      {data.originReferenceId ? <p>입고 #{data.originReferenceId}</p> : null}
    </Card>
  );
}

function BatchNode({ data }: NodeProps<FlowNode>) {
  return (
    <Card tone="batch">
      <Handle type="source" position={Position.Right} />
      <strong>한 번에 등록한 작업</strong>
      <p>{data.creationBatchSize}건</p>
    </Card>
  );
}

function WorkNode({ data }: NodeProps<FlowNode>) {
  return (
    <Card tone={data.selected ? "selected" : "work"}>
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
      <strong className="line-clamp-2">{data.title}</strong>
      <p>
        {data.workType} #{data.workOperationId}
      </p>
      <p>
        {data.workDate} · {data.status}
      </p>
      {data.varietyNames.length ? (
        <p className="truncate">{data.varietyNames.join(", ")}</p>
      ) : null}
    </Card>
  );
}

function MutationNode({ data }: NodeProps<FlowNode>) {
  return (
    <Card tone="mutation">
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
      <strong>{data.mutationType}</strong>
      <p>Mutation #{data.mutationId}</p>
      <p>{data.effectiveBusinessDate}</p>
    </Card>
  );
}

function StateNode({ data }: NodeProps<FlowNode>) {
  return (
    <Card tone="state">
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
      <strong>
        난 묶음 #{data.orchidGroupId} · r{data.stateRevision}
      </strong>
      <p>{data.state?.varietyName ?? "품종 정보 없음"}</p>
      <p>
        {data.state?.status ?? "-"} · {data.state?.quantity ?? "-"}분
      </p>
      <p>
        {[
          data.state?.houseNumber && `${data.state.houseNumber}동`,
          data.state?.physicalBedNumber &&
            `${data.state.physicalBedNumber}다이`,
          data.state?.bedZoneName,
        ]
          .filter(Boolean)
          .join(" · ")}
      </p>
    </Card>
  );
}

function JunctionNode() {
  return (
    <div className="h-[22px] w-[22px] rotate-45 border-2 border-[#2677a8] bg-[#e8f4fa]">
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

function Card({ children, tone }: { children: React.ReactNode; tone: string }) {
  const selected = tone === "selected";
  return (
    <article
      className={`w-[240px] rounded-lg border bg-white p-3 text-xs shadow-md ${selected ? "border-[#159447] ring-2 ring-[#cde8d3]" : tone === "mutation" ? "border-[#d7c9a6]" : "border-[#cad8c9]"}`}
    >
      {children}
    </article>
  );
}

function edgeLabel(type: string, relation?: string | null) {
  if (type === "ORIGINATED") return "생성";
  if (type === "SAME_COMMAND") return "함께 등록";
  if (type === "PRECEDES") return "선행";
  if (type === "EFFECT") return "실행 효과";
  if (relation === "CORRECTS") return "보정";
  if (relation === "COMPENSATES") return "보상";
  if (relation === "SUPERSEDES") return "대체";
  if (relation === "SOURCE") return "원본";
  if (relation === "RESULT") return "결과";
  return undefined;
}

function Message({ children }: { children: React.ReactNode }) {
  return (
    <div className="px-4 py-10 text-center text-sm text-[#69756c]">
      {children}
    </div>
  );
}
