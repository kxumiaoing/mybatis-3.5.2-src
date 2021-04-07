CREATE TABLE VAS_CELEBRITY(
    "ID" NUMBER(19,0) NOT NULL ENABLE,
    "NAME" VARCHAR2(256 BYTE),
     PRIMARY KEY ("ID")
);

create or replace procedure test_top(
    v_cursor out sys_refcursor,
    v_id in numeric) is
begin
    open v_cursor for select * from vas_celebrity where id < v_id;
end;