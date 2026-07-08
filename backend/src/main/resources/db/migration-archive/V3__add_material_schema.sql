create table if not exists materials (
    id bigserial primary key,
    code varchar(50) not null unique,
    category varchar(50) not null,
    name varchar(150) not null,
    manufacturer varchar(150),
    specification varchar(150),
    stock_quantity varchar(50),
    storage_location varchar(150),
    usage text,
    is_active boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);
