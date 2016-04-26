INSERT INTO Property (_id, type, name) VALUES (1, 102, 'Szentesi haz');
INSERT INTO Property (_id, type, name) VALUES (2, 101, 'Szegedi albi');
INSERT INTO Property (_id, type, name) VALUES (3, 101, 'Londoni albi');
INSERT INTO Property (_id, type, name) VALUES (4, 201, 'Sarah storage');

INSERT INTO Room_Rooter(_id, property, type, name) VALUES(0, 1,   0, '?');
INSERT INTO Room_Rooter(_id, property, type, name) VALUES(1, 1, 102, 'Nagyszoba');
INSERT INTO Room_Rooter(_id, property, type, name) VALUES(2, 1, 102, 'Robi szoba');
INSERT INTO Room_Rooter(_id, property, type, name) VALUES(3, 1, 103, 'Konyha');
INSERT INTO Item (_id, parent, category, name) VALUES (200001, (
	select root
	from Room
	where _id = 3), 10600, 'Poharak');
INSERT INTO Item (_id, parent, category, name) VALUES (200002, 200001, 10600, 'Neon pohar 9');
INSERT INTO Item (_id, parent, category, name) VALUES (200003, 200001, 10600, 'Neon pohar 10');
INSERT INTO Room_Rooter(_id, property, type, name) VALUES(4, 1, 201, 'Spajz');
	INSERT INTO Item(_id, parent, category, name) VALUES(100005, (select root from Room where _id = 4), 1100, 'Papirdoboz kek karikakkal');
		INSERT INTO Item(_id, parent, category, name) VALUES(100006, 100005, 10600, 'Piros muanyag tanyer');
		INSERT INTO Item(_id, parent, category, name) VALUES(100007, 100005, 10600, 'Neon pohar 1');
			INSERT INTO Item(_id, parent, category, name) VALUES(110008, 100007, 13200, 'Viz');
				INSERT INTO Item(_id, parent, category, name) VALUES(110009, 110008, 3, 'H20');
		INSERT INTO Item(_id, parent, category, name) VALUES(100008, 100005, 10600, 'Neon pohar 2');
		INSERT INTO Item(_id, parent, category, name) VALUES(100009, 100005, 10600, 'Neon pohar 3');
		INSERT INTO Item(_id, parent, category, name) VALUES(100010, 100005, 10600, 'Neon pohar 4');
		INSERT INTO Item(_id, parent, category, name) VALUES(100011, 100005, 10600, 'Neon pohar 5');
		INSERT INTO Item(_id, parent, category, name) VALUES(100012, 100005, 10600, 'Neon pohar 6');
		INSERT INTO Item(_id, parent, category, name) VALUES(100013, 100005, 10600, 'Neon pohar 7');
		INSERT INTO Item(_id, parent, category, name) VALUES(100014, 100005, 10600, 'Neon pohar 8');
INSERT INTO Room_Rooter(_id, property, type, name) VALUES(5, 1, 101, 'Furdoszoba');
INSERT INTO Room_Rooter(_id, property, type, name) VALUES(6, 1, 104, 'WC');
