export type LatestRequest = {
  signal: AbortSignal;
  isCurrent: () => boolean;
};

export function createLatestRequestCoordinator() {
  let nextRequestId = 0;
  let activeRequestId: number | null = null;
  let activeController: AbortController | null = null;

  return {
    begin(): LatestRequest {
      activeController?.abort();

      const requestId = ++nextRequestId;
      const controller = new AbortController();
      activeRequestId = requestId;
      activeController = controller;

      return {
        signal: controller.signal,
        isCurrent: () =>
          activeRequestId === requestId && !controller.signal.aborted,
      };
    },

    cancel() {
      activeController?.abort();
      activeController = null;
      activeRequestId = null;
    },

    complete(request: LatestRequest) {
      if (!request.isCurrent()) {
        return false;
      }

      activeController = null;
      activeRequestId = null;
      return true;
    },
  };
}
