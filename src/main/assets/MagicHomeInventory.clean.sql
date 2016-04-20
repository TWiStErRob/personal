BEGIN TRANSACTION;

DROP VIEW IF EXISTS Room_Rooter;
DROP TABLE IF EXISTS Room;
DROP TABLE IF EXISTS RoomType;
DROP TABLE IF EXISTS RoomTypeKind;

DROP TABLE IF EXISTS Property;
DROP TABLE IF EXISTS PropertyType;
DROP TABLE IF EXISTS PropertyTypeKind;

DROP TABLE IF EXISTS List_Entry;
DROP TABLE IF EXISTS List;

DROP VIEW IF EXISTS Recents;
DROP VIEW IF EXISTS Recent_Stats;
DROP TABLE IF EXISTS Recent;

DROP VIEW IF EXISTS Item_Path_Node_Refresher;
DROP VIEW IF EXISTS Item_Path;
DROP VIEW IF EXISTS Item_Path_WITH_Node_Name;
DROP TABLE IF EXISTS Item_Path_Node;
DROP TRIGGER IF EXISTS Item_delete;
DROP TABLE IF EXISTS Item;

DROP TABLE IF EXISTS Category_Name_Cache;
DROP TABLE IF EXISTS Category_Descendant;
DROP TABLE IF EXISTS Category_Tree;
DROP TABLE IF EXISTS Category_Related;
DROP TABLE IF EXISTS Category;

DROP VIEW IF EXISTS Search_Refresher;
DROP TABLE IF EXISTS Search;

DROP TABLE IF EXISTS Log;

END TRANSACTION;
