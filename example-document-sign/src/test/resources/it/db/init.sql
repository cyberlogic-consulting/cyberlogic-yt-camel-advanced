create table document_sign_log (
    id serial primary key,
    document_number int,
    owner varchar(50),
    last_update timestamp,
    status text
);