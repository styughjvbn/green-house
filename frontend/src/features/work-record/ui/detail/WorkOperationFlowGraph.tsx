"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Background,
  BaseEdge,
  Controls,
  EdgeLabelRenderer,
  getSmoothStepPath,
  getStraightPath,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
  type Edge,
  type EdgeProps,
  type Node,
  type NodeProps,
} from "@xyflow/react";
import { GitFork } from "lucide-react";
import { useMemo, useState } from "react";
import { layoutLineageGraph } from "@/shared/lib/graph/lineageGraphLayout";
import {
  buildWorkOperationFlowGraph,
  type WorkFlowGroupNode,
  type WorkFlowNode,
} from "../../lib/workOperationFlowGraph";
import { workOperationGraphQueryOptions } from "../../model/workRecordQueryOptions";

type FlowData = WorkFlowNode & Record<string, unknown>;
type FlowNode = Node<FlowData>;

const nodeTypes = {
  origin: InboundNode,
  work: WorkNode,
  group: GroupNode,
  junction: FlowJunctionNode,
};

const edgeTypes = {
  flow: WorkFlowEdge,
};

export function WorkOperationFlowGraph({
  workOperationId,
  onSelectOperation,
}: {
  workOperationId: number;
  onSelectOperation: (id: number) => void;
}) {
  const [depth, setDepth] = useState(1);
  const query = useQuery(
    workOperationGraphQueryOptions(workOperationId, "LINEAGE", depth),
  );
  const flow = useMemo(
    () => (query.data ? toFlow(query.data) : { nodes: [], edges: [] }),
    [query.data],
  );

  return (
    <section className="overflow-hidden rounded-md border border-[#d7e1d5] bg-white">
      <header className="flex flex-wrap items-start justify-between gap-3 border-b border-[#e3e9e1] bg-[#f5f8f4] px-4 py-3">
        <div>
          <div className="flex items-center gap-2 font-bold text-[#285b37]">
            <GitFork className="h-4 w-4" aria-hidden="true" />
            작업 흐름
          </div>
          <p className="mt-1 text-xs text-[#657269]">
            투입된 난 묶음과 연결된 작업·결과 흐름입니다.
          </p>
        </div>
        <label className="flex items-center gap-2 text-xs font-semibold text-[#4e6254]">
          연결 깊이
          <select
            aria-label="작업 흐름 연결 깊이"
            className="rounded border border-[#ccd8ca] bg-white px-2 py-1"
            value={depth}
            onChange={(event) => setDepth(Number(event.target.value))}
          >
            {[1, 2, 3].map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
      </header>
      {query.isPending ? (
        <Message>작업 흐름을 불러오는 중입니다.</Message>
      ) : query.isError ? (
        <Message>작업 흐름을 불러오지 못했습니다.</Message>
      ) : !flow.nodes.length ? (
        <Message>표시할 작업 흐름이 없습니다.</Message>
      ) : (
        <>
          {query.data?.truncated ? (
            <p className="m-2 rounded bg-[#fff4cf] px-3 py-2 text-xs text-[#765711]">
              가까운 흐름부터 {query.data.maxNodes}개 노드만 표시합니다.
            </p>
          ) : null}
          <div className="flex flex-wrap gap-4 border-b border-[#e7ece5] px-4 py-2 text-[11px] font-semibold text-[#647168]">
            <Legend color="#6d826f" label="투입·입고" />
            <Legend color="#187a3d" label="기준 작업" />
            <Legend color="#6b4aa1" label="잔류·결과" />
          </div>
          <div
            className="h-[36rem] bg-[#f8faf7]"
            aria-label="난 묶음 투입과 작업 결과 흐름 그래프"
          >
            <ReactFlow
              nodes={flow.nodes}
              edges={flow.edges}
              edgeTypes={edgeTypes}
              nodeTypes={nodeTypes}
              nodesDraggable={false}
              nodesConnectable={false}
              fitView
              fitViewOptions={{ padding: 0.12, maxZoom: 1 }}
              minZoom={0.08}
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
                if (data.nodeType === "ORIGIN" && data.originReferenceId) {
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

function toFlow(graph: Parameters<typeof buildWorkOperationFlowGraph>[0]) {
  const readable = buildWorkOperationFlowGraph(graph);
  const laidOut = layoutLineageGraph(readable.nodes, readable.edges, {
    ORIGIN: { width: 210, height: 90 },
    WORK_OPERATION: { width: 260, height: 135 },
    GROUP_STATE: { width: 250, height: 175 },
    FLOW_JUNCTION: { width: 22, height: 22 },
  });
  const nodes: FlowNode[] = laidOut.map((node) => ({
    id: node.id,
    type:
      node.nodeType === "ORIGIN"
        ? "origin"
        : node.nodeType === "WORK_OPERATION"
          ? "work"
          : node.nodeType === "GROUP_STATE"
            ? "group"
            : "junction",
    position: node.position,
    data: node as FlowData,
    style:
      node.nodeType === "ORIGIN"
        ? { width: 210, opacity: node.dimmed ? 0.32 : 1 }
        : node.nodeType === "WORK_OPERATION"
          ? { width: 260, opacity: node.dimmed ? 0.32 : 1 }
          : node.nodeType === "GROUP_STATE"
            ? { width: 250, opacity: node.dimmed ? 0.32 : 1 }
            : {
                width: 22,
                height: 22,
                opacity: node.dimmed ? 0.32 : 1,
              },
  }));
  const edges: Edge[] = readable.edges.map((edge) => {
    const result = ["잔류", "결과", "처리 결과"].includes(edge.flowLabel);
    const color = edge.voided ? "#9b341e" : result ? "#6b4aa1" : "#6d826f";
    const opacity = edge.dimmed ? 0.28 : 1;
    return {
      id: edge.id,
      source: edge.sourceNodeId,
      target: edge.targetNodeId,
      type: "flow",
      label:
        edge.labelVisible === false
          ? undefined
          : edge.voided
            ? "무효화됨"
            : edge.flowLabel,
      markerEnd: { type: MarkerType.ArrowClosed, color },
      style: { stroke: color, strokeWidth: 2, opacity },
      labelStyle: { fill: color, fontSize: 12, fontWeight: 800, opacity },
      labelBgStyle: { fill: "#f8faf7" },
      labelBgPadding: [6, 4] as [number, number],
      labelBgBorderRadius: 8,
      data: { labelColor: color, labelOpacity: opacity },
    };
  });
  return { nodes, edges };
}

function FlowJunctionNode() {
  return (
    <div className="relative h-[22px] w-[22px]">
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
      <div className="absolute inset-[3px] rotate-45 rounded-sm border-2 border-[#6b4aa1] bg-[#f5effc] shadow-sm" />
    </div>
  );
}

function WorkFlowEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  markerEnd,
  style,
  label,
  data,
}: EdgeProps) {
  const horizontal = Math.abs(sourceY - targetY) < 1;
  const [path, labelX, labelY] = horizontal
    ? getStraightPath({ sourceX, sourceY, targetX, targetY })
    : getSmoothStepPath({
        sourceX,
        sourceY,
        targetX,
        targetY,
        sourcePosition,
        targetPosition,
        borderRadius: 12,
        offset: 24,
      });
  const labelColor =
    typeof data?.labelColor === "string" ? data.labelColor : "#4f6255";
  const labelOpacity =
    typeof data?.labelOpacity === "number" ? data.labelOpacity : 1;
  return (
    <>
      <BaseEdge id={id} path={path} markerEnd={markerEnd} style={style} />
      {label ? (
        <EdgeLabelRenderer>
          <span
            className="nodrag nopan absolute rounded-lg bg-[#f8faf7] px-1.5 py-0.5 text-xs font-extrabold whitespace-nowrap"
            style={{
              color: labelColor,
              opacity: labelOpacity,
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
            }}
          >
            {String(label)}
          </span>
        </EdgeLabelRenderer>
      ) : null}
    </>
  );
}

function InboundNode({ data }: NodeProps<FlowNode>) {
  if (data.nodeType !== "ORIGIN") return null;
  return (
    <article className="box-border w-full min-w-0 overflow-hidden rounded-lg border border-[#8fa190] bg-white p-3 text-left text-xs shadow-md">
      <Handle type="source" position={Position.Right} />
      <span className="rounded bg-[#edf3ec] px-2 py-0.5 text-[10px] font-bold text-[#536759]">
        투입 출처
      </span>
      <strong className="mt-2 block text-sm text-[#2d4033]">
        입고 #{data.originReferenceId}
      </strong>
      <p className="mt-1 text-[#708078]">입고 기록에서 생성</p>
    </article>
  );
}

function WorkNode({ data }: NodeProps<FlowNode>) {
  if (data.nodeType !== "WORK_OPERATION") return null;
  return (
    <article
      className={`box-border w-full min-w-0 overflow-hidden rounded-xl border-2 bg-white p-4 text-left text-xs shadow-md ${
        data.selected
          ? "border-[#187a3d] ring-2 ring-[#cde8d3]"
          : "border-[#73927d]"
      }`}
    >
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
      <div className="flex items-start justify-between gap-2">
        <strong className="line-clamp-2 min-w-0 text-base text-[#253a2c]">
          {data.title}
        </strong>
        {data.selected ? (
          <span className="shrink-0 rounded bg-[#e5f4e8] px-1.5 py-0.5 text-[10px] font-bold text-[#18713a]">
            기준
          </span>
        ) : null}
      </div>
      <p className="mt-2 font-semibold text-[#45604d]">
        {data.workType} #{data.workOperationId}
      </p>
      <p className="mt-1 text-[#718078]">
        {data.workDate} · {data.status}
      </p>
    </article>
  );
}

function GroupNode({ data }: NodeProps<FlowNode>) {
  if (data.nodeType !== "GROUP_STATE") return null;
  const group = data as WorkFlowGroupNode & Record<string, unknown>;
  const terminal = group.flowRole === "TERMINAL";
  return (
    <article
      className={`box-border w-full min-w-0 overflow-hidden rounded-lg border bg-white text-left text-xs shadow-md ${
        terminal ? "border-[#bd8279]" : "border-[#9a87bb]"
      }`}
    >
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
      <div
        className={`flex items-center justify-between gap-2 px-3 py-2 ${
          terminal ? "bg-[#fff0ee]" : "bg-[#f5effc]"
        }`}
      >
        <span className="min-w-0 truncate font-bold text-[#553b78]">
          {groupRoleLabel(group.flowRole)}
        </span>
        <span className="shrink-0 text-[10px] text-[#776889]">
          난 묶음 #{group.orchidGroupId}
        </span>
      </div>
      <div className="p-3">
        <strong className="block truncate text-sm text-[#2f3430]">
          {group.state?.varietyName ?? "품종 정보 없음"}
        </strong>
        <p className="mt-1 text-base font-bold text-[#26382c]">
          {group.state?.quantity == null
            ? "수량 정보 없음"
            : `${group.state.quantity}분`}
        </p>
        <p className="mt-1 text-[#68756c]">
          {group.state?.status ?? "종료된 상태"}
        </p>
        <p className="mt-1 truncate text-[#68756c]">{locationLabel(group)}</p>
        {group.changes.length ? (
          <div className="mt-3 space-y-1 border-t border-[#ebe5ef] pt-2">
            {group.changes.slice(0, 3).map((change) => (
              <p
                className="truncate text-[11px] text-[#705390]"
                key={change.key}
              >
                <b>{change.label}</b> {change.before} → {change.after}
              </p>
            ))}
          </div>
        ) : null}
      </div>
    </article>
  );
}

function groupRoleLabel(role: WorkFlowGroupNode["flowRole"]) {
  if (role === "INPUT") return "투입 묶음";
  if (role === "RESIDUAL") return "잔류";
  if (role === "TERMINAL") return "처리 결과";
  return "결과 묶음";
}

function locationLabel(group: WorkFlowGroupNode) {
  if (!group.state) return "위치 정보 없음";
  return (
    [
      group.state.houseNumber != null ? `${group.state.houseNumber}동` : null,
      group.state.physicalBedNumber != null
        ? `${group.state.physicalBedNumber}다이`
        : null,
      group.state.bedZoneName,
    ]
      .filter(Boolean)
      .join(" · ") || "위치 정보 없음"
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        className="block h-0 w-6 border-t-2"
        style={{ borderColor: color }}
      />
      {label}
    </span>
  );
}

function Message({ children }: { children: React.ReactNode }) {
  return (
    <div className="px-4 py-10 text-center text-sm text-[#69756c]">
      {children}
    </div>
  );
}
