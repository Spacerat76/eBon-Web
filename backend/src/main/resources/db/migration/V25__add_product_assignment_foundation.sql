create table product_family (
    id bigserial primary key,
    name varchar(255) not null unique,
    default_category_id bigint references category(id) on delete set null,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table product_variant (
    id bigserial primary key,
    product_family_id bigint not null references product_family(id) on delete cascade,
    name varchar(255) not null,
    unit_quantity numeric(12,3),
    unit varchar(32),
    package_quantity integer,
    package_description varchar(255),
    total_quantity numeric(12,3),
    total_unit varchar(32),
    gtin varchar(32),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_product_variant_family_name unique (product_family_id, name),
    constraint uk_product_variant_id_family unique (id, product_family_id),
    constraint uk_product_variant_gtin unique (gtin)
);

create table product_rule (
    id bigserial primary key,
    product_family_id bigint not null references product_family(id) on delete cascade,
    product_variant_id bigint,
    store_name varchar(255),
    match_type varchar(32) not null,
    match_value varchar(512) not null,
    priority integer not null default 100,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_product_rule_variant_family
        foreign key (product_variant_id, product_family_id)
        references product_variant (id, product_family_id),
    constraint chk_product_rule_match_type
        check (match_type in ('CONTAINS', 'STARTS_WITH', 'ENDS_WITH', 'EXACT', 'REGEX')),
    constraint chk_product_rule_priority check (priority >= 0)
);

alter table receipt_item
    add column product_family_id bigint references product_family(id) on delete set null,
    add column product_variant_id bigint,
    add column product_assignment_source varchar(32),
    add column product_assignment_status varchar(32),
    add column product_assignment_confidence numeric(4,3),
    add column product_assignment_updated_at timestamptz,
    add column exclude_from_product_price_comparison boolean not null default false,
    add column product_price_exclusion_reason text,
    add constraint fk_receipt_item_product_variant_family
        foreign key (product_variant_id, product_family_id)
        references product_variant (id, product_family_id),
    add constraint chk_receipt_item_product_variant_family
        check (product_variant_id is null or product_family_id is not null),
    add constraint chk_receipt_item_product_assignment_source
        check (product_assignment_source is null or product_assignment_source in ('RULE', 'AI', 'MANUAL', 'HISTORY')),
    add constraint chk_receipt_item_product_assignment_status
        check (product_assignment_status is null or product_assignment_status in (
            'CONFIRMED', 'AUTO_ASSIGNED', 'NEEDS_REVIEW', 'REJECTED', 'NO_PRODUCT')),
    add constraint chk_receipt_item_product_assignment_confidence
        check (product_assignment_confidence is null or product_assignment_confidence between 0.000 and 1.000),
    add constraint chk_receipt_item_product_assignment_source_requires_family
        check (product_assignment_source is null or product_family_id is not null),
    add constraint chk_receipt_item_product_assignment_state
        check (product_assignment_status is not null or (
            product_family_id is null and product_variant_id is null and product_assignment_source is null));

create table product_assignment_log (
    id bigserial primary key,
    receipt_item_id bigint not null references receipt_item(id) on delete cascade,
    product_family_id bigint references product_family(id) on delete set null,
    product_variant_id bigint,
    source varchar(32) not null,
    status varchar(32) not null,
    confidence numeric(4,3),
    model_used varchar(128),
    decision_reason varchar(255),
    created_at timestamptz not null default now(),
    constraint fk_product_assignment_log_variant_family
        foreign key (product_variant_id, product_family_id)
        references product_variant (id, product_family_id),
    constraint chk_product_assignment_log_source
        check (source in ('RULE', 'AI', 'MANUAL', 'HISTORY')),
    constraint chk_product_assignment_log_status
        check (status in ('CONFIRMED', 'AUTO_ASSIGNED', 'NEEDS_REVIEW', 'REJECTED', 'NO_PRODUCT')),
    constraint chk_product_assignment_log_confidence
        check (confidence is null or confidence between 0.000 and 1.000)
);

create index idx_product_family_active_name on product_family (is_active, name);
create index idx_product_variant_family_active_name on product_variant (product_family_id, is_active, name);
create index idx_product_rule_active_store_priority on product_rule (is_active, store_name, priority, id);
create index idx_product_rule_active_priority on product_rule (is_active, priority, id);
create index idx_receipt_item_product_family on receipt_item (product_family_id);
create index idx_receipt_item_product_variant on receipt_item (product_variant_id);
create index idx_receipt_item_product_assignment_status on receipt_item (product_assignment_status);
create index idx_product_assignment_log_item_created on product_assignment_log (receipt_item_id, created_at desc);

insert into app_settings (key, value, description)
values
    ('product_history_min_confirmed_matches', '3', 'Minimum trusted product assignments required for history matching'),
    ('product_history_min_variant_share', '0.900', 'Minimum trusted variant share required for history matching')
on conflict (key) do nothing;
