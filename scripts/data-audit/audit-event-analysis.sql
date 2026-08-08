-- 최근 난 묶음 보정
SELECT
    occurred_at,
    actor_id,
    client_instance_id,
    entity_id,
    physical_bed_id,
    zone_id,
    variety_id,
    changed_fields,
    before_data,
    after_data
FROM audit_events
WHERE source = 'ORCHID_GROUP_CORRECTION'
ORDER BY occurred_at DESC;

-- 같은 다이·품종에서 5분 안에 서로 다른 구역을 연속 보정한 후보
SELECT
    first_event.id AS first_event_id,
    second_event.id AS second_event_id,
    first_event.actor_id,
    first_event.client_instance_id,
    first_event.physical_bed_id,
    first_event.variety_id,
    first_event.zone_id AS first_zone_id,
    second_event.zone_id AS second_zone_id,
    second_event.occurred_at - first_event.occurred_at AS interval
FROM audit_events first_event
JOIN audit_events second_event
  ON second_event.id > first_event.id
 AND first_event.source = 'ORCHID_GROUP_CORRECTION'
 AND second_event.source = 'ORCHID_GROUP_CORRECTION'
 AND second_event.physical_bed_id = first_event.physical_bed_id
 AND second_event.variety_id = first_event.variety_id
 AND second_event.zone_id IS DISTINCT FROM first_event.zone_id
 AND second_event.occurred_at BETWEEN first_event.occurred_at
                                  AND first_event.occurred_at + interval '5 minutes'
 AND second_event.actor_id IS NOT DISTINCT FROM first_event.actor_id
 AND second_event.client_instance_id IS NOT DISTINCT FROM first_event.client_instance_id;
