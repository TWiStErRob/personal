-- This file contains the current schema of the database.
-- Any changes to this file need to be accompanied by a version bump in `Database` class,
-- and an additional `.upgrade.<version>.sql` file to make the change for existing users.

-- The minimum SQL version is 3.8.6 (Android 5.0/21).
-- The target SQL version was 3.39.2 (Android 14/34) at the time of last update.
-- All versions are listed here: https://stackoverflow.com/a/4377116/253468

-- Notes
-- ;--NOTEOS is needed in trigger bodies so statement execution to android driver is delayed until correct semicolon
-- RAISE(action, msg) doesn't support expressions
-- Be careful with WHEN conditions in triggers for NULLABLE columns <> returns NULL, need to check if IS NULL changed
-- WITH clause support was added in SQLite 3.8.3, first to support it is Android 5.0 (3.8.6)


-- Usage: insert into Log(message) values ('Log message');
CREATE TABLE Log (
	_id         INTEGER      NOT NULL,
	at          DATETIME     DEFAULT (STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW')),
	message     TEXT,
	PRIMARY KEY(_id AUTOINCREMENT)
);

CREATE TABLE Category (
	_id         INTEGER      NOT NULL,
	name        VARCHAR      NOT NULL, -- string resource name
	image       VARCHAR      NOT NULL, -- raw resource name
	parent      INTEGER      /*NULL*/
		CONSTRAINT fk_Category_parent
			REFERENCES Category(_id)
			ON UPDATE CASCADE
			ON DELETE CASCADE,
	PRIMARY KEY(_id AUTOINCREMENT),
	UNIQUE(name),
	CHECK (_id <> parent)
);
CREATE INDEX Category_parent ON Category(parent);

CREATE TRIGGER Category_insert
AFTER INSERT ON Category
BEGIN
	insert into Category_Name_Cache values(new.name, NULL);--NOTEOS
END;

-- Materialized View to speed up other views and queries
CREATE TABLE Category_Descendant (
	category   INTEGER NOT NULL,
	descendant INTEGER NOT NULL,
	level      INTEGER NOT NULL
);
CREATE INDEX Category_Descendant_category_descendant ON Category_Descendant (category, descendant);
CREATE INDEX Category_Descendant_descendant_category ON Category_Descendant (descendant, category);

-- Assumes root -> level1 -> level2 -> level3 maximum depth
CREATE VIEW Category_Tree AS
	select
		c0._id                                                       as _id,
		c0.name                                                      as name,
		c0.image                                                     as image,
		3 - ((c1._id IS NULL) + (c2._id IS NULL) + (c3._id IS NULL)) as level,
		c0.parent                                                    as parent,
		COALESCE(c3._id, c2._id, c1._id, c0._id)                     as root,
		(select count(*) from Category where parent = c0._id)        as children,
		(select count(*) from Category_Descendant where category = c0._id and category <> descendant) as descendants,
		(
			0 < (select count(*) from Category c1 where c1.parent = c0._id
        		and 0 < (select count(*) from Category c2 where c2.parent = c1._id))
        	and
        	0 < (select count(*) from Category c1 where c1.parent = c0._id
        		and 0 = (select count(*) from Category c2 where c2.parent = c1._id))
        )                                                            as mixed
	from Category c0
	left join Category c1 on c0.parent = c1._id
	left join Category c2 on c1.parent = c2._id
	left join Category c3 on c2.parent = c3._id
	order by c0.name, c1.name, c2.name, c3.name
;

CREATE VIEW Category_Related AS
		select
			'descendant'  as source,
			c._id         as category,
			cd.descendant as related
		from Category c
		join Category_Descendant cd ON c._id = cd.category and cd.category <> cd.descendant
	UNION ALL
		select
			'siblings1' as source,
			cp0._id     as category,
			cs1._id     as related
		from Category cp0
		join Category cp1 ON cp0.parent = cp1._id
		join Category cs1 ON cs1.parent = cp1._id
	UNION ALL
		select
			'siblings1-sub' as source,
			cp0._id         as category,
			cd.descendant   as related
		from Category cp0
		join Category cp1 ON cp0.parent = cp1._id
		join Category cs1 ON cs1.parent = cp1._id and cp0._id <> cs1._id
		join Category_Descendant cd ON cs1._id = cd.category
		                               and cd.category <> cd.descendant
		                               and exists (select 1 from Category where parent = cd.descendant)
	UNION ALL
		select
			'siblings2' as source,
			cp0._id     as category,
			cs2._id     as related
		from Category cp0
		join Category cp1 ON cp0.parent = cp1._id
		join Category cp2 ON cp1.parent = cp2._id
		join Category cs2 ON cs2.parent = cp2._id
	UNION ALL
		select
			'siblings2-sub' as source,
			cp0._id         as category,
			cd.descendant   as related
		from Category cp0
		join Category cp1 ON cp0.parent = cp1._id
		join Category cp2 ON cp1.parent = cp2._id
		join Category cs2 ON cs2.parent = cp2._id and cp1._id <> cs2._id
		join Category_Descendant cd ON cs2._id = cd.category
		                               and cd.category <> cd.descendant
		                               and exists (select 1 from Category where parent = cd.descendant)
	UNION ALL
		select
			'siblings3' as source,
			cp0._id     as category,
			cs3._id     as related
		from Category cp0
		join Category cp1 ON cp0.parent = cp1._id
		join Category cp2 ON cp1.parent = cp2._id
		join Category cp3 ON cp2.parent = cp3._id
		join Category cs3 ON cs3.parent = cp3._id
	UNION ALL
		select
			'siblings3-sub' as source,
			cp0._id         as category,
			cd.descendant   as related
		from Category cp0
		join Category cp1 ON cp0.parent = cp1._id
		join Category cp2 ON cp1.parent = cp2._id
		join Category cp3 ON cp2.parent = cp3._id
		join Category cs3 ON cs3.parent = cp3._id and cp2._id <> cs3._id
		join Category_Descendant cd ON cs3._id = cd.category
		                               and cd.category <> cd.descendant
		                               and exists (select 1 from Category where parent = cd.descendant)
	UNION ALL
		select
			'toplevel' as source,
			c._id      as category,
			cs._id     as related
		from Category c
		join Category cs
		where
			cs.parent is null
	UNION ALL
		select
			'toplevel-sub' as source,
			c._id          as category,
			cs._id         as related
		from Category c
		join Category cs ON cs.parent is not null
		                    and not exists (select 1 from Category_Descendant where category = c._id and descendant = cs._id)
		join Category cp ON cp._id = cs.parent
		                    and (cp.parent is null or exists (select 1 from Category where parent = cs._id))
		where
			c.parent is null
;


CREATE TABLE Category_Name_Cache (
	key         VARCHAR      NOT NULL, -- string resource name
	value       NVARCHAR     /*NULL*/, -- translated display name by Android resources
	PRIMARY KEY(key)
);

CREATE TRIGGER Category_Name_Cache_prevent_rename
AFTER UPDATE OF key ON Category_Name_Cache
BEGIN
	select RAISE(ABORT, 'Cannot change Category_Name_Cache.key column!');--NOTEOS
END;

CREATE TRIGGER Category_Name_Cache_insert
AFTER INSERT ON Category_Name_Cache
BEGIN
	--insert into Log(message) values ('Category_Name_Cache_insert on (' || new.key || ', ' || ifNULL(new.value, 'NULL') || ')');--NOTEOS
	insert into Search_Refresher(_id) select itemID from Item_Path where categoryName = new.key;--NOTEOS
END;

CREATE TRIGGER Category_Name_Cache_update
AFTER UPDATE OF value ON Category_Name_Cache
WHEN ifNULL(old.value, '') <> ifNULL(new.value, '')
BEGIN
	--insert into Log(message) values ('Category_Name_Cache_update on (' || new.key || ', ' || ifNULL(old.value, 'NULL') || ' -> ' || ifNULL(new.value, 'NULL') || ')');--NOTEOS
	insert into Search_Refresher(_id) select itemID from Item_Path where categoryName = new.key;--NOTEOS
END;

CREATE TRIGGER Category_Name_Cache_delete
AFTER DELETE ON Category_Name_Cache
BEGIN
	--insert into Log(message) values ('Category_Name_Cache_delete on (' || old.key || ', ' || ifNULL(old.value, 'NULL') || ')');--NOTEOS
	insert into Search_Refresher(_id) select itemID from Item_Path where categoryName = old.key;--NOTEOS
END;

CREATE TABLE Image (
	_id     INTEGER  NOT NULL,
	data    BLOB     NOT NULL, -- JPEG image bytes
	updated DATETIME NOT NULL DEFAULT (STRFTIME('%s', 'NOW') * 1000),
	PRIMARY KEY (_id AUTOINCREMENT),
	CHECK (0 < length (data))
);

CREATE TRIGGER Image_image
AFTER UPDATE OF data ON Image
	WHEN old.updated = new.updated
BEGIN
	update Image
	set updated = STRFTIME('%s', CURRENT_TIMESTAMP) * 1000
	where _id = new._id;--NOTEOS
END;

CREATE TABLE Item (
	_id         INTEGER      NOT NULL,
	name        NVARCHAR     NOT NULL, -- user entered
	description TEXT         /*NULL*/, -- user entered
	image       INTEGER      /*NULL*/ DEFAULT NULL
		CONSTRAINT fk_Item_image
		REFERENCES Image (_id)
		ON UPDATE CASCADE
		ON DELETE SET DEFAULT,
	category    INTEGER      NOT NULL DEFAULT 0 -- uncategorized
		CONSTRAINT fk_Item_category
			REFERENCES Category(_id)
			ON UPDATE CASCADE
			ON DELETE SET DEFAULT,
	parent      INTEGER      /*NULL*/ /*NO DEFAULT*/ -- NULL -> ROOT item
		CONSTRAINT fk_Item_parent
			REFERENCES Item(_id)
			ON UPDATE CASCADE
			ON DELETE CASCADE,
	PRIMARY KEY(_id AUTOINCREMENT),
	UNIQUE (parent, name),
	CHECK (_id <> parent),
	CHECK (0 < length(name))
);
CREATE INDEX Item_category ON Item(category);
CREATE INDEX Item_parent ON Item (parent);

CREATE TRIGGER Item_insert
AFTER INSERT ON Item
BEGIN
	--insert into Log(message) values ('Item_insert on (' || new._id || ', ' || new.name || ', ' || ifNULL(new.image, 'NULL') || ', ' || new.category || ', ' || ifNULL(new.parent, 'NULL') || '): ' || 'started');--NOTEOS
	insert into Item_Path_Node_Refresher(_id) values (new._id);--NOTEOS
	--insert into Log(message) values ('Item_insert on (' || new._id || ', ' || new.name || ', ' || ifNULL(new.image, 'NULL') || ', ' || new.category || ', ' || ifNULL(new.parent, 'NULL') || '): ' || 'finished');--NOTEOS
END;

CREATE TRIGGER Item_delete
AFTER DELETE ON Item
BEGIN
	--insert into Log(message) values ('Item_delete on (' || old._id || ', ' || old.name || ', ' || ifNULL(old.image, 'NULL') || '): '  || 'started');--NOTEOS
	delete from Image where _id = old.image;--NOTEOS
	insert into Item_Path_Node_Refresher(_id) values (old._id);--NOTEOS
	--insert into Log(message) values ('Item_delete on (' || old._id || ', ' || old.name || ', ' || ifNULL(old.image, 'NULL') || '): '  || 'finished');--NOTEOS
END;

CREATE TRIGGER Item_move
AFTER UPDATE OF parent ON Item
WHEN old.parent <> new.parent or ((old.parent IS NULL) <> (new.parent IS NULL))
BEGIN
	--insert into Log(message) values ('Item_move on (' || new._id || ', ' || ifNULL(old.parent, 'NULL') || '->' || ifNULL(new.parent, 'NULL') || '): '  || 'started');--NOTEOS
	select RAISE(ABORT, 'Cannot change Item.parent nullity: NULL -> NOT NULL!') where old.parent IS NULL and new.parent IS NOT NULL;--NOTEOS
	select RAISE(ABORT, 'Cannot change Item.parent nullity: NOT NULL -> NULL!') where old.parent IS NOT NULL and new.parent IS NULL;--NOTEOS
	-- CONSIDER check for recursive move to prevent loops in the tree
	-- Refresh all old descendants' hierarchy (including this item)
	insert into Item_Path_Node_Refresher(_id)
		select item from Item_Path_Node
		where node = new._id
	;--NOTEOS
	--insert into Log(message) values ('Item_move on (' || new._id || ', ' || ifNULL(old.parent, 'NULL') || '->' || ifNULL(new.parent, 'NULL') || '): '  || 'finished');--NOTEOS
END;

CREATE TRIGGER Item_rename
AFTER UPDATE OF name ON Item
WHEN old.name <> new.name
BEGIN
	--insert into Log(message) values ('Item_rename on (' || new._id || ', ' || old.name || '->' || new.name || '): '  || 'started');--NOTEOS
	-- Refresh all descendants' Search data (name changed for this item and location changed all descendants)
	insert into Search_Refresher (_id)
		select item from Item_Path_Node
		where node = new._id
	;--NOTEOS
	--insert into Log(message) values ('Item_rename on (' || new._id || ', ' || old.name || '->' || new.name || '): '  || 'finished');--NOTEOS
END;

CREATE TRIGGER Item_image
AFTER UPDATE OF image ON Item
	WHEN old.image <> new.image or ((old.image IS NULL) <> (new.image IS NULL))
BEGIN
	--insert into Log(message) values ('Item_image on (' || new._id || ', ' || old.name || '->' || new.name || ', ' || ifNULL(old.image, 'NULL') || '->' || ifNULL(new.image, 'NULL') || '): '  || 'started');--NOTEOS
	delete from Image where _id = old.image;--NOTEOS
	--insert into Log(message) values ('Item_image on (' || new._id || ', ' || old.name || '->' || new.name || ', ' || ifNULL(old.image, 'NULL') || '->' || ifNULL(new.image, 'NULL') || '): '  || 'finished');--NOTEOS
END;

CREATE TRIGGER Item_categoryChange
AFTER UPDATE OF category ON Item
WHEN old.category <> new.category
BEGIN
	--insert into Log(message) values ('Item_rename on (' || new._id || ', ' || old.name || '->' || new.name || ', ' || old.category || '->' || new.category || '): '  || 'started');--NOTEOS
	insert into Search_Refresher(_id) values (new._id);--NOTEOS
	--insert into Log(message) values ('Item_rename on (' || new._id || ', ' || old.name || '->' || new.name || ', ' || old.category || '->' || new.category || '): '  || 'finished');--NOTEOS
END;


CREATE TABLE PropertyTypeKind (
	_id         INTEGER      NOT NULL,
	name        VARCHAR      NOT NULL, -- string resource name
	image       VARCHAR      NOT NULL, -- raw resource name
	PRIMARY KEY(_id),
	UNIQUE (name)
);

CREATE TABLE PropertyType (
	_id         INTEGER      NOT NULL,
	name        VARCHAR      NOT NULL, -- string resource name
	image       VARCHAR      /*NULL*/, -- raw resource name
	kind        INTEGER      NOT NULL
		CONSTRAINT fk_PropertyType_kind
		REFERENCES PropertyTypeKind(_id)
		ON UPDATE CASCADE
		ON DELETE RESTRICT,
	PRIMARY KEY(_id),
	UNIQUE (name)
);

CREATE TABLE Property (
	_id         INTEGER      NOT NULL,
	name        NVARCHAR     NOT NULL, -- user entered
	description TEXT         /*NULL*/, -- user entered
	image       INTEGER      /*NULL*/ DEFAULT NULL
		CONSTRAINT fk_Property_image
		REFERENCES Image (_id)
		ON UPDATE CASCADE
		ON DELETE SET DEFAULT,
	type        INTEGER      NOT NULL DEFAULT 0 -- other
		CONSTRAINT fk_Property_type
			REFERENCES PropertyType(_id)
			ON UPDATE CASCADE
			ON DELETE SET DEFAULT,
	PRIMARY KEY(_id AUTOINCREMENT),
	UNIQUE (name),
	CHECK (0 < length(name))
);

CREATE TRIGGER Property_image
AFTER UPDATE OF image ON Property
	WHEN old.image <> new.image or ((old.image IS NULL) <> (new.image IS NULL))
BEGIN
	--insert into Log(message) values ('Property_image on (' || new._id || ', ' || old.name || '->' || new.name || ', ' || ifNULL(old.image, 'NULL') || '->' || ifNULL(new.image, 'NULL') || '): '  || 'started');--NOTEOS
	delete from Image where _id = old.image;--NOTEOS
	--insert into Log(message) values ('Property_image on (' || new._id || ', ' || old.name || '->' || new.name || ', ' || ifNULL(old.image, 'NULL') || '->' || ifNULL(new.image, 'NULL') || '): '  || 'finished');--NOTEOS
END;

CREATE TRIGGER Property_delete
AFTER DELETE ON Property
BEGIN
	--insert into Log(message) values ('Property_delete on (' || old._id || ', ' || old.name || ', ' || ifNULL(old.image, 'NULL') || '): '  || 'started');--NOTEOS
	delete from Image where _id = old.image;--NOTEOS
	--insert into Log(message) values ('Property_delete on (' || old._id || ', ' || old.name || ', ' || ifNULL(old.image, 'NULL') || '): '  || 'finished');--NOTEOS
END;


CREATE TABLE RoomTypeKind (
	_id         INTEGER      NOT NULL,
	name        VARCHAR      NOT NULL, -- string resource name
	image       VARCHAR      NOT NULL, -- raw resource name
	PRIMARY KEY(_id),
	UNIQUE (name)
);

CREATE TABLE RoomType (
	_id         INTEGER      NOT NULL,
	name        VARCHAR      NOT NULL, -- string resource name
	image       VARCHAR      /*NULL*/, -- raw resource name
	kind        INTEGER      NOT NULL
		CONSTRAINT fk_RoomType_kind
			REFERENCES RoomTypeKind(_id)
			ON UPDATE CASCADE
			ON DELETE RESTRICT,
	PRIMARY KEY(_id),
	UNIQUE (name)
);

CREATE TABLE Room (
	_id         INTEGER      NOT NULL,
	name        NVARCHAR     NOT NULL, -- user entered
	description TEXT         /*NULL*/, -- user entered
	image       INTEGER      /*NULL*/ DEFAULT NULL
		CONSTRAINT fk_Room_image
		REFERENCES Image (_id)
		ON UPDATE CASCADE
		ON DELETE SET DEFAULT,
	type        INTEGER      NOT NULL DEFAULT 0 -- other
		CONSTRAINT fk_Room_type
			REFERENCES RoomType(_id)
			ON UPDATE CASCADE
			ON DELETE SET DEFAULT,
	root        INTEGER      NOT NULL
		CONSTRAINT fk_Room_root
			REFERENCES Item(_id)
			ON UPDATE CASCADE
			ON DELETE RESTRICT, -- need to delete the room to delete the root
	property    INTEGER      NOT NULL
		CONSTRAINT fk_Room_property
			REFERENCES Property(_id)
			ON UPDATE CASCADE
			ON DELETE CASCADE,
	PRIMARY KEY(_id AUTOINCREMENT),
	UNIQUE (property, name),
	CHECK (0 < length(name))
);
CREATE INDEX Room_root ON Room(root);

CREATE TRIGGER Room_image
AFTER UPDATE OF image ON Room
	WHEN old.image <> new.image or ((old.image IS NULL) <> (new.image IS NULL))
BEGIN
	--insert into Log(message) values ('Room_image on (' || new._id || ', ' || old.name || '->' || new.name || ', ' || ifNULL(old.image, 'NULL') || '->' || ifNULL(new.image, 'NULL') || '): '  || 'started');--NOTEOS
	delete from Image where _id = old.image;--NOTEOS
	--insert into Log(message) values ('Room_image on (' || new._id || ', ' || old.name || '->' || new.name || ', ' || ifNULL(old.image, 'NULL') || '->' || ifNULL(new.image, 'NULL') || '): '  || 'finished');--NOTEOS
END;

CREATE TRIGGER Room_delete
AFTER DELETE ON Room
BEGIN
	--insert into Log(message) values ('Room_delete on (' || old._id || ', ' || old.root || '): '  || 'started');--NOTEOS
	delete from Item where _id = old.root;--NOTEOS
	delete from Image where _id = old.image;--NOTEOS
	--insert into Log(message) values ('Room_delete on (' || old._id || ', ' || old.root || '): '  || 'finished');--NOTEOS
END;

CREATE TRIGGER Room_move
AFTER UPDATE OF property ON Room
BEGIN
	--insert into Log(message) values ('Room_move on (' || new._id || ', ' || old.property || '->' || new.property || ', ' || new.root || ', ' || new.name || '): ' || 'started');--NOTEOS
	insert into Search_Refresher(_id)
		select ip.itemID from Item_Path ip
		where ip.rootItemID <> ip.itemID and ip.roomID = new._id
	;--NOTEOS
	--insert into Log(message) values ('Room_move on (' || new._id || ', ' || old.property || '->' || new.property || ', ' || new.root || ', ' || new.name || '): ' || 'finished');--NOTEOS
END;

CREATE VIEW Room_Rooter AS select * from Room;

CREATE TRIGGER Room_Rooter_Auto
INSTEAD OF INSERT ON Room_Rooter
WHEN new.root IS NULL
BEGIN
	insert into Item(name, category, parent)
		values ('ROOT', -1, NULL)
	;--NOTEOS
	insert into Room(_id, name, description, type, root, property)
		values (new._id, new.name, new.description, new.type, last_insert_rowid(), new.property)
	;--NOTEOS
END;

CREATE TRIGGER Room_Rooter_Transparent
INSTEAD OF INSERT ON Room_Rooter
WHEN (new.root IS NOT NULL)
BEGIN
	insert into Room(_id, name, image, type, root, property)
		values (new._id, new.name, new.image, new.type, new.root, new.property)
	;--NOTEOS
END;


CREATE TABLE Recent (
	_id         INTEGER,
	visit       DATETIME DEFAULT (STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW'))
);

CREATE TRIGGER Recent_insert
AFTER INSERT ON Recent
BEGIN
	-- Delete old items, all those who have more than 100 items before them.
	-- Could simply delete the oldest item,
	-- this is a safety so that the recents never exceed 100 entries.
	delete from Recent where 100 < (
		select count(*) from Recent r where Recent.visit <= r.visit
	);--NOTEOS
END;

CREATE VIEW Recent_Stats AS
	select
		_id,
		count(*) as population,
		count(*)/cast(s.count as float) as percentage,
		max(visit) as visit,
		(julianday(max(visit)) - julianday(s.firstVisit)) / (julianday(s.lastVisit) - julianday(s.firstVisit)) as recency
	from Recent,
	(
		select
			min(visit) as firstVisit,
			max(visit) as lastVisit,
			--datetime(avg(julianday(visit))) as meanVisit,
			count(*) as count
		from Recent
	) s
	group by _id
;

CREATE VIEW Recents AS
	select
		r._id,
		r.population,
		r.percentage,
		count(distinct rp._id) as populationRank,
		r.visit,
		r.recency,
		count(distinct rr._id) as visitRank
	from Recent_Stats r
	join Recent_Stats rp on r.population <= rp.population
	join Recent_Stats rr on r.visit <= rr.visit
	group by r._id
;


CREATE TABLE List (
	_id         INTEGER      NOT NULL,
	name        VARCHAR      NOT NULL, -- user entered
	PRIMARY KEY(_id AUTOINCREMENT),
   	UNIQUE (name),
   	CHECK (0 < length(name))
);

CREATE TABLE List_Entry (
	list        INTEGER      NOT NULL
		CONSTRAINT fk_List_Entry_list
			REFERENCES List(_id)
			ON UPDATE CASCADE
			ON DELETE CASCADE,
	item        INTEGER      NOT NULL
		CONSTRAINT fk_List_Entry_item
			REFERENCES Item(_id)
			ON UPDATE CASCADE
			ON DELETE CASCADE,
	UNIQUE (list, item)
);


/**
Structural lookup table to work around not having Hierarchical queries: e.g. STARTS WITH/CONNECT BY/PRIOR in Oracle.
WITH [RECURSIVE] CTEs are supported, but only on very new devices at this time.
The table needs to be updated when there's a structural change (e.g. Item changes parent), see Item_Path_Node_Refresher


This table can give answers to the following questions:
-- Who are the ascendants of an Item? (excluding self)
-- (ordered by closest (=parent) to farthest (=room root))
select node from Item_Path_Node where node <> item and item = 110008 order by level desc
-- Who are the descendants of an Item? (excluding self)
select item from Item_Path_Node where node <> item and node = 100005
-- How deep in the tree is an Item?
-- (level 0 is room root, level 1 is directly in the room, level 2 is child of something directly in the room etc.)
select level from Item_Path_Node where node = item and item = 110008
-- What is the root Item for an Item?
select root from Item_Path_Node where node = item and item = 110008
-- Which Room is an item in? (based on previous)
select r._id from Room r join Item_Path_Node ipn ON ipn.root = r.root where ipn.node = ipn.item and ipn.item = 110008
-- What's in a Room? (excluding root)
select ipn.item from Item_Path_Node ipn join Room r ON ipn.node = r.root where ipn.item <> ipn.node and r._id = 4
*/
CREATE TABLE Item_Path_Node (
	item        INTEGER      NOT NULL
		CONSTRAINT fk_Item_Path_Node_item
			REFERENCES Item(_id)
			ON UPDATE CASCADE
			ON DELETE CASCADE,
	level       INTEGER      NOT NULL,
	node        INTEGER      NOT NULL
		CONSTRAINT fk_Item_Path_Node_node
			REFERENCES Item(_id)
			ON UPDATE CASCADE
			ON DELETE NO ACTION,
	root        INTEGER      NOT NULL
		CONSTRAINT fk_Item_Path_Node_root
			REFERENCES Item(_id)
			ON UPDATE CASCADE
			ON DELETE NO ACTION
);
CREATE INDEX Item_Path_Node_item_node ON Item_Path_Node(item, node);
CREATE INDEX Item_Path_Node_node_item ON Item_Path_Node(node, item);
CREATE INDEX Item_Path_Node_root      ON Item_Path_Node(root);

CREATE TRIGGER Item_Path_Node_traverse
AFTER INSERT ON Item_Path_Node
BEGIN
	--insert into Log(message) values ('Item_Path_Node_traverse on (' || new.item || ', ' || new.level || ', ' || new.node || ', ' || new.root || '): ' || 'started');--NOTEOS
	-- Go up in the Tree inserting a new item for each ascendant
	insert into Item_Path_Node (item, level, node, root)
	-- level is increasing as we go up the tree, but later it'll be flipped
	-- root column is temporary, will be replaced as well
	-- see Item_Path_Node_refresh for more details
		select new.item, new.level + 1, i.parent, i.parent from Item i
		where i._id = new.node and i.parent IS NOT NULL
	;--NOTEOS
	--insert into Log(message) values ('Item_Path_Node_traverse on (' || new.item || ', ' || new.level || ', ' || new.node || ', ' || new.root || '): ' || 'finished');--NOTEOS
END;

CREATE VIEW Item_Path_Node_Refresher AS
	SELECT NULL as _id
;

CREATE TRIGGER Item_Path_Node_refresh
INSTEAD OF INSERT ON Item_Path_Node_Refresher
BEGIN
	--insert into Log(message) values ('Item_Path_Node_refresh on (' || new._id || '): ' || 'started');--NOTEOS
	-- Clean up stale values for this item, the hierarchy will be fully rebuilt from current structure for the item
	delete from Item_Path_Node where item = new._id;--NOTEOS

	-- insert a new row for the item this is called for this will trigger Item_Path_Node_traverse
	-- and all ascendants will be inserted as well
	insert into Item_Path_Node (item, node, level, root)
	-- level starts from this item for now, it'll be flipped later
	-- root is just a temporary value
		select new._id, new._id, 0, new._id
		from Item where _id = new._id -- need to restrict in case the item doesn't exist any more
	;--NOTEOS

	-- Flip level values: level([current..root]) = [0..depth] -> [depth..0]
	-- Before inserting all ascendants the information of how deep an item is is not available.
	-- The levels are originally inserted starting from the current item.
	-- This query get's the max level and does a simple "1-x" transformation on it so they're reversed
	-- Having the level values decreasing from item to root makes it easier to query later
	update Item_Path_Node
		set level = (select max(level) from Item_Path_Node where item = new._id) - level
		where item = new._id
	;--NOTEOS

	-- Find the real value for root column. Now that the ascendants are in place (each having node == root at this time)
	-- and the level being set: 0 as root .. n as leaf, it's easy to find the root node
	update Item_Path_Node
	set root = (select node from Item_Path_Node where item = new._id and level = 0)
		where item = new._id
	;--NOTEOS

	-- CONSIDER moving it somewhere else
	-- This is called here for now, because the structural change affects Search.location
	insert into Search_Refresher(_id)
		values (new._id)
	;--NOTEOS
	--insert into Log(message) values ('Item_Path_Node_refresh on (' || new._id || '): ' || 'finished');--NOTEOS
END;

-- FIXME this is either badly named or not useful, usages are confusing
CREATE VIEW Item_Path AS
	select
		p._id      as propertyID,
		p.name     as propertyName,
		r._id      as roomID,
		r.name     as roomName,
		r.root     as rootItemID,
		ipn.level  as itemLevel,
		i._id      as itemID,
		i.name     as itemName,
		c._id      as categoryID,
		c.name     as categoryName
	from Item           i
	join Item_Path_Node ipn ON ipn.item = i._id and ipn.node = i._id
	join Room           r   ON ipn.root = r.root
	join Property       p   ON r.property = p._id
	join Category       c   ON i.category = c._id
;

-- FTS4 available since Android 3.0 (API 11) SQLite 3.7.4: https://www.sqlite.org/releaselog/3_7_4.html
-- Current minimum is Android 5.0 (API 21) SQLite 3.8.6: https://www.sqlite.org/releaselog/3_8_6.html,
-- which means "The unicode61 tokenizer is now included in FTS4 by default."
CREATE VIRTUAL TABLE Search USING FTS4 (
    -- https://www.twisterrob.net/blog/2023/10/sqlite-unicode61-remove-diacritics-2.html
	tokenize=unicode61 'remove_diacritics=1',
	name,
	location
);

CREATE VIEW Search_Refresher AS
	select NULL as _id
;

CREATE TRIGGER Search_refresh
INSTEAD OF INSERT ON Search_Refresher
BEGIN
	--insert into Log(message) values ('Search_refresh on (' || new._id || ')');--NOTEOS
	delete from Search where rowid = new._id;--NOTEOS
	insert into Search (rowid, name, location)
		select
			i._id                                           as rowid,
			i.name || ' (' || ifNULL(cnc.value, '?') || ')' as name,
			group_concat(Path.part, ' ◀ ')                  as location
		from Item                     i
		join Category                 c   ON i.category = c._id
		left join Category_Name_Cache cnc ON c.name = cnc.key
		left join (
			select n.name as part
			from Item_Path_Node ipn
			join Item           n   ON ipn.node = n._id
			where ipn.item = new._id and ipn.item <> ipn.node and ipn.node <> ipn.root
			order by level DESC
		) as Path
		where i._id = new._id and c._id <> -1 -- category_internal
	;--NOTEOS
END;
