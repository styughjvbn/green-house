import assert from "node:assert/strict";
import test from "node:test";
import { QueryClient, QueryObserver } from "@tanstack/react-query";
import {
  farmMutationQueryKeys,
  invalidateWorkAndInboundQueries,
} from "../src/entities/farm/model/farmMutationQueries.ts";

function client() {
  return new QueryClient({
    defaultOptions: {
      queries: { staleTime: 30_000, retry: false, gcTime: Infinity },
    },
  });
}

test("work changes invalidate fresh inbound and work caches, without clearing unrelated inventory", async () => {
  const cache = client();
  const work = [...farmMutationQueryKeys.workOperations, 1];
  const inbound = [...farmMutationQueryKeys.inboundRecords, "record", 2];
  const inboundPage = [...farmMutationQueryKeys.inboundRecords, {}, 0, 20];
  const unrelated = ["inventory", "materials"];
  cache.setQueryData(work, { status: "COMPLETED" });
  cache.setQueryData(inbound, { status: "PLACED" });
  cache.setQueryData(inboundPage, [{ status: "PLACED" }]);
  cache.setQueryData(unrelated, ["material"]);
  await invalidateWorkAndInboundQueries(cache);
  for (const key of [work, inbound, inboundPage]) {
    assert.equal(cache.getQueryState(key).isInvalidated, true);
  }
  assert.equal(cache.getQueryState(unrelated).isInvalidated, false);
  let calls = 0;
  const result = await cache.fetchQuery({
    queryKey: inbound,
    queryFn: async () => {
      calls++;
      return { status: "POTTING_PENDING", availableActions: ["CANCEL"] };
    },
  });
  assert.equal(calls, 1);
  assert.equal(result.status, "POTTING_PENDING");
  assert.deepEqual(result.availableActions, ["CANCEL"]);
  cache.clear();
});

test("inbound changes refetch fresh work detail, pages, calendar and graph on next access", async () => {
  const cache = client();
  const queries = [
    [...farmMutationQueryKeys.workOperations, 1],
    [...farmMutationQueryKeys.workOperations, "page", "ALL", {}, 0, 20],
    [...farmMutationQueryKeys.workOperations, "calendar", "ALL", "2026-10", ""],
    [...farmMutationQueryKeys.workOperations, 1, "graph", "SUMMARY", 1],
    [...farmMutationQueryKeys.workOperations, 1, "details"],
    [...farmMutationQueryKeys.workOperations, 1, "relations", "ALL"],
  ];
  for (const queryKey of queries) cache.setQueryData(queryKey, "COMPLETED");
  await invalidateWorkAndInboundQueries(cache);
  let calls = 0;
  for (const queryKey of queries) {
    const result = await cache.fetchQuery({
      queryKey,
      queryFn: async () => {
        calls++;
        return "VOIDED";
      },
    });
    assert.equal(result, "VOIDED");
  }
  assert.equal(calls, queries.length);
  cache.clear();
});

test("visible work and inbound queries refetch immediately and invalidation awaits both", async () => {
  const cache = client();
  const keys = [
    [...farmMutationQueryKeys.workOperations, 1],
    [...farmMutationQueryKeys.inboundRecords, "record", 2],
  ];
  const release = [];
  const observers = keys.map((queryKey) => {
    cache.setQueryData(queryKey, "old");
    return new QueryObserver(cache, {
      queryKey,
      queryFn: () => new Promise((resolve) => release.push(resolve)),
    });
  });
  const unsubscribe = observers.map((observer) => observer.subscribe(() => {}));
  let completed = false;
  const invalidation = invalidateWorkAndInboundQueries(cache).then(() => {
    completed = true;
  });
  assert.equal(release.length, 2);
  assert.equal(completed, false);
  release[0]("new-work");
  await Promise.resolve();
  assert.equal(completed, false);
  release[1]("new-inbound");
  await invalidation;
  assert.equal(completed, true);
  assert.equal(cache.getQueryData(keys[0]), "new-work");
  assert.equal(cache.getQueryData(keys[1]), "new-inbound");
  for (const stop of unsubscribe) stop();
  cache.clear();
});
