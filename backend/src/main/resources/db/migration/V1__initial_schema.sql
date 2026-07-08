--
-- Name: auction_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auction_attempts (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    attempt_no integer NOT NULL,
    attempt_status character varying(255) NOT NULL,
    auction_date date NOT NULL,
    failed_reason character varying(255),
    memo text,
    shipment_lot_id bigint NOT NULL
);


--
-- Name: auction_attempts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.auction_attempts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: auction_attempts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.auction_attempts_id_seq OWNED BY public.auction_attempts.id;


--
-- Name: auction_lot_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auction_lot_status_history (
    id bigint NOT NULL,
    changed_at timestamp without time zone NOT NULL,
    memo text,
    new_status character varying(255) NOT NULL,
    previous_status character varying(255),
    reason character varying(255) NOT NULL,
    worker character varying(255),
    shipment_lot_id bigint NOT NULL
);


--
-- Name: auction_lot_status_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.auction_lot_status_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: auction_lot_status_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.auction_lot_status_history_id_seq OWNED BY public.auction_lot_status_history.id;


--
-- Name: auction_result_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auction_result_lines (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    amount integer NOT NULL,
    auction_date date NOT NULL,
    auction_grade character varying(255),
    inspection_status character varying(255) NOT NULL,
    note text,
    quantity integer NOT NULL,
    unit_price integer NOT NULL,
    auction_attempt_id bigint NOT NULL
);


--
-- Name: auction_result_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.auction_result_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: auction_result_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.auction_result_lines_id_seq OWNED BY public.auction_result_lines.id;


--
-- Name: auction_settlement_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auction_settlement_lines (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    amount bigint NOT NULL,
    line_meta_json jsonb,
    quantity integer NOT NULL,
    status character varying(255) NOT NULL,
    unit_price integer NOT NULL,
    auction_result_line_id bigint NOT NULL,
    auction_shipment_lot_id bigint NOT NULL,
    settlement_id bigint NOT NULL
);


--
-- Name: auction_settlement_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.auction_settlement_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: auction_settlement_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.auction_settlement_lines_id_seq OWNED BY public.auction_settlement_lines.id;


--
-- Name: auction_settlements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auction_settlements (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    auction_date date NOT NULL,
    confirmed_at timestamp without time zone,
    confirmed_by character varying(255),
    deduction_amount bigint NOT NULL,
    expected_deposit_amount bigint NOT NULL,
    expected_payment_date date,
    fee_amount bigint NOT NULL,
    gross_amount bigint NOT NULL,
    memo text,
    paid_amount bigint NOT NULL,
    payment_meta_json jsonb,
    remaining_amount bigint NOT NULL,
    result_received_at timestamp without time zone,
    status character varying(255) NOT NULL,
    auction_house_id bigint NOT NULL
);


--
-- Name: auction_settlements_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.auction_settlements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: auction_settlements_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.auction_settlements_id_seq OWNED BY public.auction_settlements.id;


--
-- Name: auction_shipment_lots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auction_shipment_lots (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    boxes integer,
    current_status character varying(255) NOT NULL,
    item_name character varying(255) NOT NULL,
    memo text,
    returned_quantity integer NOT NULL,
    shipment_grade character varying(255),
    shipped_quantity integer NOT NULL,
    sold_quantity integer NOT NULL,
    variety_name character varying(255) NOT NULL,
    waiting_quantity integer NOT NULL,
    shipment_id bigint NOT NULL,
    return_confirmed_date date
);


--
-- Name: auction_shipment_lots_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.auction_shipment_lots_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: auction_shipment_lots_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.auction_shipment_lots_id_seq OWNED BY public.auction_shipment_lots.id;


--
-- Name: auction_shipments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auction_shipments (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    memo text,
    shipment_date date NOT NULL,
    status character varying(255) NOT NULL,
    auction_house_id bigint NOT NULL
);


--
-- Name: auction_shipments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.auction_shipments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: auction_shipments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.auction_shipments_id_seq OWNED BY public.auction_shipments.id;


--
-- Name: bed_zone_capacities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bed_zone_capacities (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    bed_zone_id bigint NOT NULL,
    placement_type character varying(100) NOT NULL,
    pot_size character varying(50),
    capacity_mode character varying(20) NOT NULL,
    unit_span numeric(6,2) NOT NULL,
    capacity_value integer NOT NULL,
    is_allowed boolean NOT NULL,
    memo text
);


--
-- Name: bed_zone_capacities_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bed_zone_capacities_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bed_zone_capacities_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bed_zone_capacities_id_seq OWNED BY public.bed_zone_capacities.id;


--
-- Name: bed_zones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bed_zones (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    is_active boolean NOT NULL,
    memo text,
    name character varying(255) NOT NULL,
    side character varying(255) NOT NULL,
    sort_order integer NOT NULL,
    zone_type character varying(255) NOT NULL,
    physical_bed_id bigint NOT NULL
);


--
-- Name: bed_zones_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bed_zones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bed_zones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bed_zones_id_seq OWNED BY public.bed_zones.id;


--
-- Name: business_partners; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.business_partners (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    address text,
    memo text,
    name character varying(255) NOT NULL,
    owner_name character varying(255),
    phone character varying(255),
    partner_type character varying(255) DEFAULT 'WHOLESALE'::character varying NOT NULL,
    is_active boolean DEFAULT true NOT NULL
);


--
-- Name: business_partners_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.business_partners_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: business_partners_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.business_partners_id_seq OWNED BY public.business_partners.id;


--
-- Name: houses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.houses (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    memo text,
    name character varying(255) NOT NULL,
    number integer NOT NULL
);


--
-- Name: houses_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.houses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: houses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.houses_id_seq OWNED BY public.houses.id;


--
-- Name: inbound_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inbound_records (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    inbound_date date NOT NULL,
    inbound_type character varying(50) NOT NULL,
    status character varying(50) NOT NULL,
    bottle_count integer,
    estimated_quantity integer,
    actual_quantity integer,
    temp_location character varying(255),
    potting_due_date date,
    potting_date date,
    pot_size character varying(50),
    age_year integer,
    growth_stage character varying(100),
    placement_type character varying(100),
    tray_count integer,
    worker character varying(50),
    memo text,
    variety_id bigint NOT NULL,
    bed_zone_id bigint,
    created_orchid_group_id bigint
);


--
-- Name: inbound_records_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inbound_records_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inbound_records_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inbound_records_id_seq OWNED BY public.inbound_records.id;


--
-- Name: materials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.materials (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    category character varying(50) NOT NULL,
    name character varying(150) NOT NULL,
    manufacturer character varying(150),
    specification character varying(150),
    stock_quantity character varying(50),
    storage_location character varying(150),
    usage text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: materials_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.materials_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: materials_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.materials_id_seq OWNED BY public.materials.id;


--
-- Name: orchid_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orchid_groups (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    age_year integer,
    genus character varying(255),
    memo text,
    placement_type character varying(255),
    pot_size character varying(255),
    quantity integer NOT NULL,
    sort_order integer NOT NULL,
    status character varying(255) NOT NULL,
    tray_count integer,
    variety_name character varying(255) NOT NULL,
    bed_zone_id bigint NOT NULL,
    split_placement_allowed boolean,
    variety_id bigint,
    inbound_record_id bigint,
    start_position numeric(6,2),
    end_position numeric(6,2),
    reserved_quantity integer DEFAULT 0 NOT NULL
);


--
-- Name: orchid_groups_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.orchid_groups_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: orchid_groups_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.orchid_groups_id_seq OWNED BY public.orchid_groups.id;


--
-- Name: partner_balance_summaries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.partner_balance_summaries (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    credit_balance bigint NOT NULL,
    receivable_balance bigint NOT NULL,
    unapplied_payment_amount bigint NOT NULL,
    partner_id bigint NOT NULL,
    summary_json jsonb,
    last_payment_event_id bigint
);


--
-- Name: partner_balance_summaries_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.partner_balance_summaries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: partner_balance_summaries_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.partner_balance_summaries_id_seq OWNED BY public.partner_balance_summaries.id;


--
-- Name: partner_payment_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.partner_payment_events (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    amount bigint NOT NULL,
    created_by character varying(255),
    depositor_name character varying(255),
    description text,
    event_date date NOT NULL,
    event_type character varying(255) NOT NULL,
    memo text,
    payment_method character varying(255),
    status character varying(255) NOT NULL,
    target_id bigint,
    target_type character varying(255),
    unapplied_amount bigint NOT NULL,
    parent_event_id bigint,
    partner_id bigint NOT NULL,
    allocation_payload jsonb,
    balance_snapshot_json jsonb,
    event_time time without time zone,
    external_uid character varying(255),
    match_payload jsonb,
    raw_payload jsonb
);


--
-- Name: partner_payment_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.partner_payment_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: partner_payment_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.partner_payment_events_id_seq OWNED BY public.partner_payment_events.id;


--
-- Name: partner_settlement_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.partner_settlement_settings (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    allow_prepayment boolean NOT NULL,
    amount_tolerance bigint NOT NULL,
    auto_match_enabled boolean NOT NULL,
    auto_settle_enabled boolean NOT NULL,
    credit_auto_apply_enabled boolean NOT NULL,
    depositor_aliases jsonb,
    memo text,
    payment_day_mode character varying(255) NOT NULL,
    payment_delay_days integer NOT NULL,
    rule_json jsonb,
    settlement_unit character varying(255) NOT NULL,
    partner_id bigint NOT NULL
);


--
-- Name: partner_settlement_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.partner_settlement_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: partner_settlement_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.partner_settlement_settings_id_seq OWNED BY public.partner_settlement_settings.id;


--
-- Name: physical_beds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.physical_beds (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    display_order integer NOT NULL,
    length_cm integer,
    memo text,
    number integer NOT NULL,
    support_interval_cm integer,
    width_cm integer,
    wire_count integer,
    house_id bigint NOT NULL,
    position_unit_count numeric(6,2),
    position_unit_label character varying(50)
);


--
-- Name: physical_beds_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.physical_beds_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: physical_beds_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.physical_beds_id_seq OWNED BY public.physical_beds.id;


--
-- Name: sales_inventory_movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_inventory_movements (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    orchid_group_id bigint NOT NULL,
    sales_slip_id bigint NOT NULL,
    sales_slip_item_id bigint NOT NULL,
    change_type character varying(50) NOT NULL,
    quantity_delta integer NOT NULL,
    memo text
);


--
-- Name: sales_inventory_movements_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_inventory_movements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_inventory_movements_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_inventory_movements_id_seq OWNED BY public.sales_inventory_movements.id;


--
-- Name: sales_slip_item_allocations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_slip_item_allocations (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    sales_slip_item_id bigint NOT NULL,
    orchid_group_id bigint NOT NULL,
    allocated_quantity integer NOT NULL
);


--
-- Name: sales_slip_item_allocations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_slip_item_allocations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_slip_item_allocations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_slip_item_allocations_id_seq OWNED BY public.sales_slip_item_allocations.id;


--
-- Name: sales_slip_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_slip_items (
    id bigint NOT NULL,
    amount integer NOT NULL,
    genus character varying(255),
    item_name character varying(255) NOT NULL,
    memo text,
    quantity integer NOT NULL,
    spec character varying(255),
    unit_price integer NOT NULL,
    sales_slip_id bigint NOT NULL,
    auction_shipment_lot_id bigint
);


--
-- Name: sales_slip_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_slip_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_slip_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_slip_items_id_seq OWNED BY public.sales_slip_items.id;


--
-- Name: sales_slips; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_slips (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    memo text,
    payment_method character varying(255),
    payment_status character varying(255) NOT NULL,
    sale_date date NOT NULL,
    sales_status character varying(255) NOT NULL,
    slip_number character varying(255) NOT NULL,
    total_amount integer NOT NULL,
    partner_id bigint NOT NULL,
    sales_type character varying(255),
    auction_shipment_id bigint,
    expected_payment_date date,
    paid_amount bigint,
    remaining_amount bigint
);


--
-- Name: sales_slips_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_slips_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_slips_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_slips_id_seq OWNED BY public.sales_slips.id;


--
-- Name: varieties; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.varieties (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    code character varying(50) NOT NULL,
    genus character varying(100) NOT NULL,
    name character varying(150) NOT NULL,
    alias character varying(150),
    default_pot_size character varying(50),
    description text,
    sale_enabled boolean NOT NULL,
    is_active boolean NOT NULL,
    memo text
);


--
-- Name: varieties_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.varieties_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: varieties_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.varieties_id_seq OWNED BY public.varieties.id;


--
-- Name: work_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_records (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    dilution_ratio character varying(255),
    from_bed_zone_id bigint,
    material_name character varying(255),
    memo text,
    quantity character varying(255),
    target_id bigint,
    target_type character varying(255) NOT NULL,
    to_bed_zone_id bigint,
    work_date date NOT NULL,
    work_type character varying(255) NOT NULL,
    worker character varying(255),
    work_type_id bigint
);


--
-- Name: work_records_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.work_records_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: work_records_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.work_records_id_seq OWNED BY public.work_records.id;


--
-- Name: work_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_types (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    is_active boolean NOT NULL,
    code character varying(50) NOT NULL,
    is_default boolean NOT NULL,
    name character varying(50) NOT NULL,
    sort_order integer NOT NULL,
    is_system boolean NOT NULL,
    template character varying(30) NOT NULL
);


--
-- Name: work_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.work_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: work_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.work_types_id_seq OWNED BY public.work_types.id;


--
-- Name: auction_attempts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_attempts ALTER COLUMN id SET DEFAULT nextval('public.auction_attempts_id_seq'::regclass);


--
-- Name: auction_lot_status_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_lot_status_history ALTER COLUMN id SET DEFAULT nextval('public.auction_lot_status_history_id_seq'::regclass);


--
-- Name: auction_result_lines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_result_lines ALTER COLUMN id SET DEFAULT nextval('public.auction_result_lines_id_seq'::regclass);


--
-- Name: auction_settlement_lines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_settlement_lines ALTER COLUMN id SET DEFAULT nextval('public.auction_settlement_lines_id_seq'::regclass);


--
-- Name: auction_settlements id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_settlements ALTER COLUMN id SET DEFAULT nextval('public.auction_settlements_id_seq'::regclass);


--
-- Name: auction_shipment_lots id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_shipment_lots ALTER COLUMN id SET DEFAULT nextval('public.auction_shipment_lots_id_seq'::regclass);


--
-- Name: auction_shipments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_shipments ALTER COLUMN id SET DEFAULT nextval('public.auction_shipments_id_seq'::regclass);


--
-- Name: bed_zone_capacities id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bed_zone_capacities ALTER COLUMN id SET DEFAULT nextval('public.bed_zone_capacities_id_seq'::regclass);


--
-- Name: bed_zones id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bed_zones ALTER COLUMN id SET DEFAULT nextval('public.bed_zones_id_seq'::regclass);


--
-- Name: business_partners id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_partners ALTER COLUMN id SET DEFAULT nextval('public.business_partners_id_seq'::regclass);


--
-- Name: houses id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.houses ALTER COLUMN id SET DEFAULT nextval('public.houses_id_seq'::regclass);


--
-- Name: inbound_records id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_records ALTER COLUMN id SET DEFAULT nextval('public.inbound_records_id_seq'::regclass);


--
-- Name: materials id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials ALTER COLUMN id SET DEFAULT nextval('public.materials_id_seq'::regclass);


--
-- Name: orchid_groups id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orchid_groups ALTER COLUMN id SET DEFAULT nextval('public.orchid_groups_id_seq'::regclass);


--
-- Name: partner_balance_summaries id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_balance_summaries ALTER COLUMN id SET DEFAULT nextval('public.partner_balance_summaries_id_seq'::regclass);


--
-- Name: partner_payment_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_payment_events ALTER COLUMN id SET DEFAULT nextval('public.partner_payment_events_id_seq'::regclass);


--
-- Name: partner_settlement_settings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_settlement_settings ALTER COLUMN id SET DEFAULT nextval('public.partner_settlement_settings_id_seq'::regclass);


--
-- Name: physical_beds id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_beds ALTER COLUMN id SET DEFAULT nextval('public.physical_beds_id_seq'::regclass);


--
-- Name: sales_inventory_movements id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_inventory_movements ALTER COLUMN id SET DEFAULT nextval('public.sales_inventory_movements_id_seq'::regclass);


--
-- Name: sales_slip_item_allocations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slip_item_allocations ALTER COLUMN id SET DEFAULT nextval('public.sales_slip_item_allocations_id_seq'::regclass);


--
-- Name: sales_slip_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slip_items ALTER COLUMN id SET DEFAULT nextval('public.sales_slip_items_id_seq'::regclass);


--
-- Name: sales_slips id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slips ALTER COLUMN id SET DEFAULT nextval('public.sales_slips_id_seq'::regclass);


--
-- Name: varieties id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.varieties ALTER COLUMN id SET DEFAULT nextval('public.varieties_id_seq'::regclass);


--
-- Name: work_records id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_records ALTER COLUMN id SET DEFAULT nextval('public.work_records_id_seq'::regclass);


--
-- Name: work_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_types ALTER COLUMN id SET DEFAULT nextval('public.work_types_id_seq'::regclass);


--
-- Name: auction_attempts auction_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_attempts
    ADD CONSTRAINT auction_attempts_pkey PRIMARY KEY (id);


--
-- Name: auction_lot_status_history auction_lot_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_lot_status_history
    ADD CONSTRAINT auction_lot_status_history_pkey PRIMARY KEY (id);


--
-- Name: auction_result_lines auction_result_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_result_lines
    ADD CONSTRAINT auction_result_lines_pkey PRIMARY KEY (id);


--
-- Name: auction_settlement_lines auction_settlement_lines_auction_result_line_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_settlement_lines
    ADD CONSTRAINT auction_settlement_lines_auction_result_line_id_key UNIQUE (auction_result_line_id);


--
-- Name: auction_settlement_lines auction_settlement_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_settlement_lines
    ADD CONSTRAINT auction_settlement_lines_pkey PRIMARY KEY (id);


--
-- Name: auction_settlements auction_settlements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_settlements
    ADD CONSTRAINT auction_settlements_pkey PRIMARY KEY (id);


--
-- Name: auction_shipment_lots auction_shipment_lots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_shipment_lots
    ADD CONSTRAINT auction_shipment_lots_pkey PRIMARY KEY (id);


--
-- Name: auction_shipments auction_shipments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_shipments
    ADD CONSTRAINT auction_shipments_pkey PRIMARY KEY (id);


--
-- Name: bed_zone_capacities bed_zone_capacities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bed_zone_capacities
    ADD CONSTRAINT bed_zone_capacities_pkey PRIMARY KEY (id);


--
-- Name: bed_zones bed_zones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bed_zones
    ADD CONSTRAINT bed_zones_pkey PRIMARY KEY (id);


--
-- Name: business_partners business_partners_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_partners
    ADD CONSTRAINT business_partners_pkey PRIMARY KEY (id);


--
-- Name: houses houses_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.houses
    ADD CONSTRAINT houses_number_key UNIQUE (number);


--
-- Name: houses houses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.houses
    ADD CONSTRAINT houses_pkey PRIMARY KEY (id);


--
-- Name: inbound_records inbound_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_records
    ADD CONSTRAINT inbound_records_pkey PRIMARY KEY (id);


--
-- Name: materials materials_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials
    ADD CONSTRAINT materials_code_key UNIQUE (code);


--
-- Name: materials materials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials
    ADD CONSTRAINT materials_pkey PRIMARY KEY (id);


--
-- Name: orchid_groups orchid_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orchid_groups
    ADD CONSTRAINT orchid_groups_pkey PRIMARY KEY (id);


--
-- Name: partner_balance_summaries partner_balance_summaries_last_payment_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_balance_summaries
    ADD CONSTRAINT partner_balance_summaries_last_payment_event_id_key UNIQUE (last_payment_event_id);


--
-- Name: partner_balance_summaries partner_balance_summaries_partner_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_balance_summaries
    ADD CONSTRAINT partner_balance_summaries_partner_id_key UNIQUE (partner_id);


--
-- Name: partner_balance_summaries partner_balance_summaries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_balance_summaries
    ADD CONSTRAINT partner_balance_summaries_pkey PRIMARY KEY (id);


--
-- Name: partner_payment_events partner_payment_events_external_uid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_payment_events
    ADD CONSTRAINT partner_payment_events_external_uid_key UNIQUE (external_uid);


--
-- Name: partner_payment_events partner_payment_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_payment_events
    ADD CONSTRAINT partner_payment_events_pkey PRIMARY KEY (id);


--
-- Name: partner_settlement_settings partner_settlement_settings_partner_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_settlement_settings
    ADD CONSTRAINT partner_settlement_settings_partner_id_key UNIQUE (partner_id);


--
-- Name: partner_settlement_settings partner_settlement_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_settlement_settings
    ADD CONSTRAINT partner_settlement_settings_pkey PRIMARY KEY (id);


--
-- Name: physical_beds physical_beds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_beds
    ADD CONSTRAINT physical_beds_pkey PRIMARY KEY (id);


--
-- Name: sales_inventory_movements sales_inventory_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_inventory_movements
    ADD CONSTRAINT sales_inventory_movements_pkey PRIMARY KEY (id);


--
-- Name: sales_slip_item_allocations sales_slip_item_allocations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slip_item_allocations
    ADD CONSTRAINT sales_slip_item_allocations_pkey PRIMARY KEY (id);


--
-- Name: sales_slip_items sales_slip_items_auction_shipment_lot_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slip_items
    ADD CONSTRAINT sales_slip_items_auction_shipment_lot_id_key UNIQUE (auction_shipment_lot_id);


--
-- Name: sales_slip_items sales_slip_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slip_items
    ADD CONSTRAINT sales_slip_items_pkey PRIMARY KEY (id);


--
-- Name: sales_slips sales_slips_auction_shipment_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slips
    ADD CONSTRAINT sales_slips_auction_shipment_id_key UNIQUE (auction_shipment_id);


--
-- Name: sales_slips sales_slips_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slips
    ADD CONSTRAINT sales_slips_pkey PRIMARY KEY (id);


--
-- Name: sales_slips sales_slips_slip_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slips
    ADD CONSTRAINT sales_slips_slip_number_key UNIQUE (slip_number);


--
-- Name: auction_settlements uk_auction_settlement_house_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_settlements
    ADD CONSTRAINT uk_auction_settlement_house_date UNIQUE (auction_house_id, auction_date);


--
-- Name: physical_beds uk_physical_beds_house_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_beds
    ADD CONSTRAINT uk_physical_beds_house_number UNIQUE (house_id, number);


--
-- Name: varieties varieties_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.varieties
    ADD CONSTRAINT varieties_code_key UNIQUE (code);


--
-- Name: varieties varieties_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.varieties
    ADD CONSTRAINT varieties_pkey PRIMARY KEY (id);


--
-- Name: work_records work_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_records
    ADD CONSTRAINT work_records_pkey PRIMARY KEY (id);


--
-- Name: work_types work_types_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_types
    ADD CONSTRAINT work_types_code_key UNIQUE (code);


--
-- Name: work_types work_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_types
    ADD CONSTRAINT work_types_pkey PRIMARY KEY (id);


--
-- Name: idx_auction_shipments_date_house; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auction_shipments_date_house ON public.auction_shipments USING btree (shipment_date, auction_house_id);


--
-- Name: idx_partner_payment_partner_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_partner_payment_partner_date ON public.partner_payment_events USING btree (partner_id, event_date);


--
-- Name: idx_partner_payment_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_partner_payment_target ON public.partner_payment_events USING btree (target_type, target_id);


--
-- Name: idx_sales_slips_partner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_slips_partner_id ON public.sales_slips USING btree (partner_id);


--
-- Name: uk_bed_zone_capacities_rule; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_bed_zone_capacities_rule ON public.bed_zone_capacities USING btree (bed_zone_id, placement_type, COALESCE(pot_size, ''::character varying), capacity_mode);


--
-- Name: auction_attempts auction_attempts_shipment_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_attempts
    ADD CONSTRAINT auction_attempts_shipment_lot_id_fkey FOREIGN KEY (shipment_lot_id) REFERENCES public.auction_shipment_lots(id);


--
-- Name: auction_lot_status_history auction_lot_status_history_shipment_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_lot_status_history
    ADD CONSTRAINT auction_lot_status_history_shipment_lot_id_fkey FOREIGN KEY (shipment_lot_id) REFERENCES public.auction_shipment_lots(id);


--
-- Name: auction_result_lines auction_result_lines_auction_attempt_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_result_lines
    ADD CONSTRAINT auction_result_lines_auction_attempt_id_fkey FOREIGN KEY (auction_attempt_id) REFERENCES public.auction_attempts(id);


--
-- Name: auction_settlement_lines auction_settlement_lines_auction_result_line_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_settlement_lines
    ADD CONSTRAINT auction_settlement_lines_auction_result_line_id_fkey FOREIGN KEY (auction_result_line_id) REFERENCES public.auction_result_lines(id);


--
-- Name: auction_settlement_lines auction_settlement_lines_auction_shipment_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_settlement_lines
    ADD CONSTRAINT auction_settlement_lines_auction_shipment_lot_id_fkey FOREIGN KEY (auction_shipment_lot_id) REFERENCES public.auction_shipment_lots(id);


--
-- Name: auction_settlement_lines auction_settlement_lines_settlement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_settlement_lines
    ADD CONSTRAINT auction_settlement_lines_settlement_id_fkey FOREIGN KEY (settlement_id) REFERENCES public.auction_settlements(id);


--
-- Name: auction_settlements auction_settlements_auction_house_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_settlements
    ADD CONSTRAINT auction_settlements_auction_house_id_fkey FOREIGN KEY (auction_house_id) REFERENCES public.business_partners(id);


--
-- Name: auction_shipment_lots auction_shipment_lots_shipment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_shipment_lots
    ADD CONSTRAINT auction_shipment_lots_shipment_id_fkey FOREIGN KEY (shipment_id) REFERENCES public.auction_shipments(id);


--
-- Name: auction_shipments auction_shipments_auction_house_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auction_shipments
    ADD CONSTRAINT auction_shipments_auction_house_id_fkey FOREIGN KEY (auction_house_id) REFERENCES public.business_partners(id);


--
-- Name: bed_zone_capacities bed_zone_capacities_bed_zone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bed_zone_capacities
    ADD CONSTRAINT bed_zone_capacities_bed_zone_id_fkey FOREIGN KEY (bed_zone_id) REFERENCES public.bed_zones(id);


--
-- Name: bed_zones bed_zones_physical_bed_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bed_zones
    ADD CONSTRAINT bed_zones_physical_bed_id_fkey FOREIGN KEY (physical_bed_id) REFERENCES public.physical_beds(id);


--
-- Name: orchid_groups fk_orchid_groups_inbound_record; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orchid_groups
    ADD CONSTRAINT fk_orchid_groups_inbound_record FOREIGN KEY (inbound_record_id) REFERENCES public.inbound_records(id);


--
-- Name: orchid_groups fk_orchid_groups_variety; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orchid_groups
    ADD CONSTRAINT fk_orchid_groups_variety FOREIGN KEY (variety_id) REFERENCES public.varieties(id);


--
-- Name: inbound_records inbound_records_bed_zone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_records
    ADD CONSTRAINT inbound_records_bed_zone_id_fkey FOREIGN KEY (bed_zone_id) REFERENCES public.bed_zones(id);


--
-- Name: inbound_records inbound_records_created_orchid_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_records
    ADD CONSTRAINT inbound_records_created_orchid_group_id_fkey FOREIGN KEY (created_orchid_group_id) REFERENCES public.orchid_groups(id);


--
-- Name: inbound_records inbound_records_variety_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inbound_records
    ADD CONSTRAINT inbound_records_variety_id_fkey FOREIGN KEY (variety_id) REFERENCES public.varieties(id);


--
-- Name: orchid_groups orchid_groups_bed_zone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orchid_groups
    ADD CONSTRAINT orchid_groups_bed_zone_id_fkey FOREIGN KEY (bed_zone_id) REFERENCES public.bed_zones(id);


--
-- Name: partner_balance_summaries partner_balance_summaries_last_payment_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_balance_summaries
    ADD CONSTRAINT partner_balance_summaries_last_payment_event_id_fkey FOREIGN KEY (last_payment_event_id) REFERENCES public.partner_payment_events(id);


--
-- Name: partner_balance_summaries partner_balance_summaries_partner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_balance_summaries
    ADD CONSTRAINT partner_balance_summaries_partner_id_fkey FOREIGN KEY (partner_id) REFERENCES public.business_partners(id);


--
-- Name: partner_payment_events partner_payment_events_parent_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_payment_events
    ADD CONSTRAINT partner_payment_events_parent_event_id_fkey FOREIGN KEY (parent_event_id) REFERENCES public.partner_payment_events(id);


--
-- Name: partner_payment_events partner_payment_events_partner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_payment_events
    ADD CONSTRAINT partner_payment_events_partner_id_fkey FOREIGN KEY (partner_id) REFERENCES public.business_partners(id);


--
-- Name: partner_settlement_settings partner_settlement_settings_partner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partner_settlement_settings
    ADD CONSTRAINT partner_settlement_settings_partner_id_fkey FOREIGN KEY (partner_id) REFERENCES public.business_partners(id);


--
-- Name: physical_beds physical_beds_house_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_beds
    ADD CONSTRAINT physical_beds_house_id_fkey FOREIGN KEY (house_id) REFERENCES public.houses(id);


--
-- Name: sales_inventory_movements sales_inventory_movements_orchid_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_inventory_movements
    ADD CONSTRAINT sales_inventory_movements_orchid_group_id_fkey FOREIGN KEY (orchid_group_id) REFERENCES public.orchid_groups(id);


--
-- Name: sales_inventory_movements sales_inventory_movements_sales_slip_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_inventory_movements
    ADD CONSTRAINT sales_inventory_movements_sales_slip_id_fkey FOREIGN KEY (sales_slip_id) REFERENCES public.sales_slips(id);


--
-- Name: sales_inventory_movements sales_inventory_movements_sales_slip_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_inventory_movements
    ADD CONSTRAINT sales_inventory_movements_sales_slip_item_id_fkey FOREIGN KEY (sales_slip_item_id) REFERENCES public.sales_slip_items(id);


--
-- Name: sales_slip_item_allocations sales_slip_item_allocations_orchid_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slip_item_allocations
    ADD CONSTRAINT sales_slip_item_allocations_orchid_group_id_fkey FOREIGN KEY (orchid_group_id) REFERENCES public.orchid_groups(id);


--
-- Name: sales_slip_item_allocations sales_slip_item_allocations_sales_slip_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slip_item_allocations
    ADD CONSTRAINT sales_slip_item_allocations_sales_slip_item_id_fkey FOREIGN KEY (sales_slip_item_id) REFERENCES public.sales_slip_items(id) ON DELETE CASCADE;


--
-- Name: sales_slip_items sales_slip_items_auction_shipment_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slip_items
    ADD CONSTRAINT sales_slip_items_auction_shipment_lot_id_fkey FOREIGN KEY (auction_shipment_lot_id) REFERENCES public.auction_shipment_lots(id);


--
-- Name: sales_slip_items sales_slip_items_sales_slip_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slip_items
    ADD CONSTRAINT sales_slip_items_sales_slip_id_fkey FOREIGN KEY (sales_slip_id) REFERENCES public.sales_slips(id);


--
-- Name: sales_slips sales_slips_auction_shipment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slips
    ADD CONSTRAINT sales_slips_auction_shipment_id_fkey FOREIGN KEY (auction_shipment_id) REFERENCES public.auction_shipments(id);


--
-- Name: sales_slips sales_slips_partner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_slips
    ADD CONSTRAINT sales_slips_partner_id_fkey FOREIGN KEY (partner_id) REFERENCES public.business_partners(id);


--
-- Name: work_records work_records_work_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_records
    ADD CONSTRAINT work_records_work_type_id_fkey FOREIGN KEY (work_type_id) REFERENCES public.work_types(id);