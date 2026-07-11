insert into product_family (name, default_category_id)
select 'Filetraeucherling', category.id
from category
where category.name = 'Fleisch und Wurst'
on conflict (name) do update
set default_category_id = coalesce(product_family.default_category_id, excluded.default_category_id),
    updated_at = now();

insert into product_rule (
    product_family_id,
    store_name,
    match_type,
    match_value,
    priority
)
select
    family.id,
    'REWE',
    'EXACT',
    'FILETRAEUCHERL.',
    100
from product_family family
where family.name = 'Filetraeucherling'
  and not exists (
      select 1
      from product_rule existing
      where existing.product_family_id = family.id
        and existing.product_variant_id is null
        and existing.store_name = 'REWE'
        and existing.match_type = 'EXACT'
        and existing.match_value = 'FILETRAEUCHERL.'
  );
