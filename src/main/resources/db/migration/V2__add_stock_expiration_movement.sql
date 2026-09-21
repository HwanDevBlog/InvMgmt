alter table stock_ledger
    drop constraint ck_stock_ledger_movement_type;

alter table stock_ledger
    add constraint ck_stock_ledger_movement_type check (
        movement_type in ('INITIAL', 'RESERVE', 'EXPIRE', 'CANCEL', 'RETURN', 'ADJUSTMENT')
    );
