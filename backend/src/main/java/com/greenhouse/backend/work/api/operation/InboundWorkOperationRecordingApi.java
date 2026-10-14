package com.greenhouse.backend.work.api.operation;

import com.greenhouse.backend.work.api.effect.WorkMutationLink;

/** Records Farm's inbound snapshot and mutation link in the existing transaction. */
public interface InboundWorkOperationRecordingApi {

  void record(RecordInboundWorkCommand request, WorkMutationLink mutationLink);
}
