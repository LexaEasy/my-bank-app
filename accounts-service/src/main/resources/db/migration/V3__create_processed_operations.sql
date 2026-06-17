create table processed_operations (
    operation_id varchar(128) primary key,
    operation_type varchar(32) not null,
    request_hash varchar(64) not null,
    status varchar(16) not null,
    response_json text,
    created_at timestamp not null,
    updated_at timestamp not null
);
