-- Outcome of an import: RUNNING while chunks are processed, then COMPLETED or FAILED.
-- Imports recorded before this migration ran to completion.

alter table import_batch add column status varchar(16) not null default 'COMPLETED';
alter table import_batch add column failure_message varchar(500);
