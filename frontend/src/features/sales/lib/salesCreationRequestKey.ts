type RequestStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

// Keep an unresolved creation identity even when its form is closed or the screen reloads.
export function createSalesCreationRequestKey(
  createKey: () => string,
  storage: () => RequestStorage | null = () => null,
) {
  const scope = "greenhouse:sales-create-request:v1";
  let pending: string | null = null;
  return {
    get() {
      if (pending == null) {
        try {
          pending = storage()?.getItem(scope) ?? createKey();
          storage()?.setItem(scope, pending);
        } catch {
          pending ??= createKey();
        }
      }
      return pending;
    },
    complete(key: string) {
      if (pending === key) pending = null;
      try {
        if (storage()?.getItem(scope) === key) storage()?.removeItem(scope);
      } catch {
        // Browser storage is optional; the mounted screen still keeps its identity.
      }
    },
  };
}
