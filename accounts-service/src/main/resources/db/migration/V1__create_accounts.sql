create table accounts (
    id bigserial primary key,
    login varchar(64) not null unique,
    name varchar(120) not null,
    birthdate date not null,
    balance numeric(19, 2) not null,
    currency varchar(3) not null,
    version bigint not null default 0
);
