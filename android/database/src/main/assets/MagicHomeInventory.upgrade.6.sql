-- Context: https://github.com/TWiStErRob/net.twisterrob.inventory/issues/170
-- FTS3 supported only ASCII characters, FTS4 supports unicode when used with unicode61 tokenizer.

-- Recreate the Search table with new FTS4 format.
DROP TABLE IF EXISTS Search;
CREATE VIRTUAL TABLE Search USING FTS4 (
    -- https://www.twisterrob.net/blog/2023/10/sqlite-unicode61-remove-diacritics-2.html
	tokenize=unicode61 'remove_diacritics=1',
	name,
	location
);

-- Re-insert all data into the new table.
INSERT INTO Search_Refresher (_id) SELECT _id FROM Item;
-- Compact database indexes for faster search experience.
INSERT INTO Search(Search) VALUES('optimize');
