import assert from "node:assert/strict";
import test from "node:test";
import { readServerSentEvents } from "../src/shared/api/serverSentEvents.ts";

function stream(parts, cancel = () => {}) {
  return new ReadableStream({
    start(controller) {
      for (const part of parts) controller.enqueue(part);
      controller.close();
    },
    cancel,
  });
}

test("SSE parses split UTF-8, CRLF, multiline data and heartbeats", async () => {
  const bytes = new TextEncoder().encode(
    ':keepalive\r\n\r\nevent: progress\r\ndata: {"summary":\r\ndata: "품종 확인"}\r\n\r\nevent: progress\ndata: {"stage":"DONE"}\n\n',
  );
  const events = [];
  for await (const event of readServerSentEvents(
    stream(Array.from(bytes, (byte) => Uint8Array.of(byte))),
  ))
    events.push(event);
  assert.equal(events.length, 2);
  assert.equal(events[0].event, "progress");
  assert.deepEqual(JSON.parse(events[0].data), { summary: "품종 확인" });
  assert.deepEqual(JSON.parse(events[1].data), { stage: "DONE" });
});

test("SSE drops incomplete events and bounds event size", async () => {
  const events = [];
  for await (const event of readServerSentEvents(
    stream([new TextEncoder().encode("data: unfinished\n")]),
  ))
    events.push(event);
  assert.deepEqual(events, []);
  await assert.rejects(async () => {
    for await (const event of readServerSentEvents(
      stream([new TextEncoder().encode("data: " + "x".repeat(65537))]),
    )) {
      assert.ok(event);
    }
  }, /너무 큽니다/);
});

test("stopping the stream consumer releases its reader", async () => {
  let cancelled = false;
  const body = new ReadableStream({
    start(controller) {
      controller.enqueue(new TextEncoder().encode("data: first\n\n"));
    },
    cancel() {
      cancelled = true;
    },
  });
  for await (const event of readServerSentEvents(body)) {
    assert.ok(event);
    break;
  }
  assert.equal(cancelled, true);
  assert.equal(body.locked, false);
});
