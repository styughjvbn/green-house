UPDATE varieties
SET color = NULL;

WITH palette(color, position) AS (
    SELECT color, position
    FROM unnest(ARRAY[
        '#7B5432', '#668137', '#7B7037', '#317F2F',
        '#846839', '#4A7F34', '#747D3B', '#367851',
        '#A27139', '#6DA43D', '#9C9440', '#36A14B',
        '#A8883E', '#49A63A', '#899C40', '#409677',
        '#C47C45', '#92C44F', '#C1AA4E', '#3FC643',
        '#C69853', '#77C549', '#BCC054', '#4BB9A7',
        '#C39E79', '#B1C879', '#C4BB82', '#70C78A',
        '#C9B282', '#86CB72', '#BDC57D', '#74BBBE',
        '#CDB3A2', '#B5CE9C', '#CDCCA7', '#97CE9E',
        '#CEBBA1', '#A6CE92', '#C1CB9F', '#97BAC4',
        '#9D622F', '#76A136', '#998238', '#339B2C',
        '#A77D35', '#43A130', '#989F38', '#3B916C',
        '#B46E46', '#7ABE46', '#B5AA4A', '#3DB85A',
        '#BD9F4C', '#61C23D', '#A7B94B', '#48A8A1',
        '#BA9973', '#9EC176', '#BAAF78', '#67C16D',
        '#C1A57B', '#7BC468', '#BCBC76', '#6BA9B3'
    ]::varchar[]) WITH ORDINALITY AS colors(color, position)
),
ranked_varieties AS (
    SELECT
        variety.id,
        row_number() OVER (ORDER BY variety.id) AS position
    FROM varieties variety
    WHERE EXISTS (
        SELECT 1
        FROM orchid_groups orchid_group
        WHERE orchid_group.variety_id = variety.id
    )
)
UPDATE varieties variety
SET color = palette.color
FROM ranked_varieties
JOIN palette
    ON palette.position = ((ranked_varieties.position - 1) % 64) + 1
WHERE variety.id = ranked_varieties.id;