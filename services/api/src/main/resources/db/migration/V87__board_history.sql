-- The nine boards the site has listed, moved out of the page that listed them.
--
-- Names, titles, personal blurbs and photographs sat in a Vue file where nothing could ask
-- who had been on a board. They are rows now, and the page reads them.
--
-- Dating comes from the page's own history rather than from the page, which carries none:
-- the site said "goodbye board 5, hello board 6" in October 2022, was created showing the
-- sitting board in October 2020, and gained the 9th board in October 2025. One board a year,
-- changing in the autumn, so board N runs the association year beginning September 2016 + N.
-- The day of the handover is not recorded anywhere, so the year is the unit.
--
-- Seats are linked to members by an exact name where exactly one member matches. Most of
-- these people never had an account here, and those seats stand under their own name.
--
-- Idempotent throughout: a rerun adds only what is missing.

CREATE TEMPORARY TABLE board_import (
    board_name   VARCHAR(100) NOT NULL,
    start_date   DATE         NOT NULL,
    end_date     DATE         NOT NULL,
    board_image  VARCHAR(255) NULL,
    display_name VARCHAR(128) NOT NULL,
    role         VARCHAR(255) NOT NULL,
    description  TEXT         NULL,
    image        VARCHAR(255) NULL
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO board_import VALUES
('1st Board', '2017-09-01', '2018-08-31', NULL, 'Thijs “Grootbuik” Lieverse', 'Chairman', NULL, NULL),
('1st Board', '2017-09-01', '2018-08-31', NULL, 'Jan-Berend “JB” Mooijaart', 'Secretary', NULL, NULL),
('1st Board', '2017-09-01', '2018-08-31', NULL, 'Anne “Iadri” Schrader', 'Treasurer', NULL, NULL),
('1st Board', '2017-09-01', '2018-08-31', NULL, 'Stijn “Salish” Overduin', 'Commissioner of Internal Affairs', NULL, NULL),
('1st Board', '2017-09-01', '2018-08-31', NULL, 'Idwer “Fangedsheep” de Vries', 'Commissioner of External Affairs', NULL, NULL),
('2nd Board', '2018-09-01', '2019-08-31', NULL, 'Jasper “JappieXD” van Harten', 'Chairman', NULL, NULL),
('2nd Board', '2018-09-01', '2019-08-31', NULL, 'Kimberly “Agile Manifesto” Evertz', 'Secretary', NULL, NULL),
('2nd Board', '2018-09-01', '2019-08-31', NULL, 'Mauk “Dawarfmaster” Muller', 'Treasurer', NULL, NULL),
('2nd Board', '2018-09-01', '2019-08-31', NULL, 'Antal “TheMadJokerHD” van Dongen', 'Commissioner of Internal Affairs', NULL, NULL),
('2nd Board', '2018-09-01', '2019-08-31', NULL, 'Maiander “Maiantie” Eigenraam', 'Commissioner of External Affairs', NULL, NULL),
('3rd Board', '2019-09-01', '2020-08-31', NULL, 'Jander “Thoran” Gilbers', 'Chairman', NULL, NULL),
('3rd Board', '2019-09-01', '2020-08-31', NULL, 'Joran “MacVanish” Hagen', 'Secretary/Treasurer', NULL, NULL),
('3rd Board', '2019-09-01', '2020-08-31', NULL, 'Andrei “ElDonte” Raureanu', 'Commissioner of Esports', NULL, NULL),
('3rd Board', '2019-09-01', '2020-08-31', NULL, 'William “Lampekap88” Schaarman', 'Commissioner of internal affairs', NULL, NULL),
('3rd Board', '2019-09-01', '2020-08-31', NULL, 'Allysha “Meavis” Sewradj', 'Commissioner of external affairs', NULL, NULL),
('4th Board', '2020-09-01', '2021-08-31', NULL, 'Ali “Kelbinoh” Kalbiyev', 'Chair', NULL, NULL),
('4th Board', '2020-09-01', '2021-08-31', NULL, 'Tjebbe “Issie54” Iskander Sterck', 'Treasurer', NULL, NULL),
('4th Board', '2020-09-01', '2021-08-31', NULL, 'Ivo “King Cookie” Heitlager', 'Secretary and Commissioner of Esports Affairs', NULL, NULL),
('4th Board', '2020-09-01', '2021-08-31', NULL, 'Lucia "Luna" Kim', 'Commissioner of Internal Affairs', NULL, NULL),
('4th Board', '2020-09-01', '2021-08-31', NULL, 'Jose “Bear” Pratdesaba Lopez', 'Commissioner of External Affairs', NULL, NULL);

INSERT INTO board_import VALUES
('5th Board', '2021-09-01', '2022-08-31', 'board5/board5.jpg', 'Daniël "thiefzz" Floor', 'Chairman', NULL, NULL),
('5th Board', '2021-09-01', '2022-08-31', 'board5/board5.jpg', 'Bob "Bobbuz" Even', 'Treasurer and Commissioner of Esports Affairs', NULL, NULL),
('5th Board', '2021-09-01', '2022-08-31', 'board5/board5.jpg', 'Louis "Poking" Hu', 'Secretary', NULL, NULL),
('5th Board', '2021-09-01', '2022-08-31', 'board5/board5.jpg', 'Jelle "Zenga" Idzenga', 'Commissioner of Internal Affairs', NULL, NULL),
('5th Board', '2021-09-01', '2022-08-31', 'board5/board5.jpg', 'Ìlayda "Vriendelijke kebab" Hotamis', 'Commissioner of External Affairs', NULL, NULL),
('6th Board', '2022-09-01', '2023-08-31', 'board6/board6.jpg', 'Amber "Ambanana" Scholtz', 'Chair', NULL, 'board6/amber.jpg'),
('6th Board', '2022-09-01', '2023-08-31', 'board6/board6.jpg', 'Thomas "ItIsIThomas" Dekker', 'Treasurer', NULL, 'board6/thomas.jpg'),
('6th Board', '2022-09-01', '2023-08-31', 'board6/board6.jpg', 'Jelle "TheJellyMan" van Wezep', 'Secretary and Commissioner of the Esports Lounge', NULL, 'board6/jelle.jpg'),
('6th Board', '2022-09-01', '2023-08-31', 'board6/board6.jpg', 'Jonas "Clunky" Valentijn', 'Commissioner of Esports Affairs', NULL, 'board6/jonas.jpg'),
('6th Board', '2022-09-01', '2023-08-31', 'board6/board6.jpg', 'Roos "SkyeWolf" Kruk', 'Commissioner of Internal Affairs', NULL, 'board6/roos.jpg'),
('6th Board', '2022-09-01', '2023-08-31', 'board6/board6.jpg', 'Thijs "Darkneoteric" Willems', 'Commissioner of External Affairs', NULL, 'board6/thijs.jpg'),
('7th Board', '2023-09-01', '2024-08-31', 'board7/board7.jpg', 'Reini Strating', 'Chair', 'Hello, I''m Reini Strating but you can call me whatever you want – there is no limit. I''m still searching for a suitable study, that''s why this is my break year! I enjoy playing any type of games if I get invited. Always trying to have fun with others, whatever suits their boat. I joined Blueshell after a friend introduced me to it last year and got me instantly hooked with the friendliness and good times. It got me excited enough to join the board and here I am! CHAIR ready for service!', 'board7/reinout.jpg'),
('7th Board', '2023-09-01', '2024-08-31', 'board7/board7.jpg', 'Max Jansdam', 'Secretary and Commissioner of the Esports Lounge', 'Hey, I am Max Jansdam and I am the Secretary and Commissioner of the Esports Lounge affairs for this year. This year I will be a third-year Psychology bachelor. I joined Blueshell 2 years ago during corona with the idea of finding people to play smash ultimate and just dance with. While I didn’t join many events in the first year, I started joining and organizing more in my second year. After friends asked me to join the next board, I thought, why not? Let’s make this an amazing year!', 'board7/max.jpg'),
('7th Board', '2023-09-01', '2024-08-31', 'board7/board7.jpg', 'Jesse van Gameren', 'Treasurer and Commissioner of Esports Affairs', 'Hello fellow people! My name is Jesse van Gameren and I am the Treasurer and Commissioner of Esports affairs of Blueshell this year. Currently, I am in my third year of Industrial Engineering and Management. I became a member of Blueshell 2 years ago after being invited to a few events where I found a great community! I like to play games and do just about anything, as long as it''s with friends. After joining more events and committees, I wanted to try something new – to learn more myself and give back to the community that gives me so much joy. Let’s make this a wonderful year to remember!', 'board7/jesse.jpg'),
('7th Board', '2023-09-01', '2024-08-31', 'board7/board7.jpg', 'Mitchell van Poecke', 'Officer of Internal Affairs', 'Hey, what''s up! I am Mitchell van Poecke and this year I will be the Officer of Internal Affairs of Blueshell. I’m currently in my third year of Creative Technology and this will also be my third year with Blueshell. I enjoy going for a walk, working on game prototypes and, of course, playing games with friends. In my first year I attended most Minecraft events and a few game nights – which led me to join a committee. At first, the idea of a board year didn’t speak to me, but after a few months in a committee, I got excited and joined the board. I hope we can have another fun and exciting year together!', 'board7/mitchell.jpg'),
('7th Board', '2023-09-01', '2024-08-31', 'board7/board7.jpg', 'Sanne van Kooten', 'Officer of External Affairs', 'Hello! I am Sanne van Kooten, and I am this year''s Officer of External Affairs! Currently, I’m a fourth-year Biomedical Engineering bachelor. In addition to being creative and visiting concerts, I also love gaming. I joined Blueshell in the second year of my studies after participating in the Nintendo pubquiz during the kick-off, and I was immediately enthusiastic. After joining a few committees, I decided it was time to do something bigger – like being a board member. I look forward to creating an even bigger gaming community with lots of fun events!', 'board7/sanne.jpg'),
('8th Board', '2024-09-01', '2025-08-31', 'board8/board8.jpg', 'Michal Rokita', 'Chair', 'Hello! I’m Michal ''udeyy'' Rokita, 21 years old, and Chair of Blueshell. I was born in Warsaw, Poland, and spent much of my life in Belgium and the Netherlands before coming to Enschede to study Industrial Design Engineering at UT. I''ve always loved gaming (especially Valorant), but also enjoy sports like running and swimming. I’m excited to represent Blueshell and help it thrive!', 'board8/Michal.png'),
('8th Board', '2024-09-01', '2025-08-31', 'board8/board8.jpg', 'Joris Jonkers', 'Secretary and Commissioner of External Affairs', 'Heyoo! I’m Joris ‘ExtraToast’ Jonkers, 26 years old, studying Computer Science, and serving as Secretary and Commissioner of External Affairs. I’ve been gaming since childhood, from old consoles to PC gaming. I love working on old vehicles, collecting retro games, and going to concerts and festivals. I look forward to keeping Blueshell organized and connecting with more gaming enthusiasts!', 'board8/Joris.jpg'),
('8th Board', '2024-09-01', '2025-08-31', 'board8/board8.jpg', 'Chris Wong', 'Treasurer and Commissioner of Esports affairs', 'Hey there! I’m Chris ''FetaBass'' Wong, 20 years old from Apeldoorn, studying TCS (and possibly switching to BIT). I grew up with Pokémon and moved on to titles like MapleStory, RuneScape, and Minecraft. Nowadays, I’m really into Valorant, League of Legends, and CS2. I’m excited to manage our finances and help foster our esports community!', 'board8/Chris.png'),
('8th Board', '2024-09-01', '2025-08-31', 'board8/board8.jpg', 'Yannick Sloot', 'Commissioner of Internal Affairs', 'Yoo guys! I’m Yannick ''Yank'' Sloot, 20 years old from Biddinghuizen, studying International Business Administration at UT. I''ve been gaming since I was a kid—from the GBA to PC, with favorites like CS2, Terraria, and Dark Souls. Besides gaming, I enjoy music and traveling. I’m looking forward to ensuring everything runs smoothly within Blueshell!', 'board8/Yannick.png');

INSERT INTO board_import VALUES
('9th Board', '2025-09-01', '2026-08-31', 'board9/board9.jpg', 'Emma Dokter', 'Chair', 'Hi everyone, My name is Emma ''LyndisLuna'' Dokter, and I’m the 9th Chair of our beloved Blueshell. I am a Psychology student here at the University of Twente, and I have always been passionate about games, whether that is a console game like the Legend of Zelda, or a MOBA like League of Legends. I am a part of the League team “Pandora” in our association, and also created the cozy games committee “ChillCie” last year. I am extremely grateful for the opportunity to be Blueshell’s chair, and can’t wait to have an amazing year with everyone in the association. So if you see me around, don’t hesitate to come have a chat with me!', 'board9/Emma.jpg'),
('9th Board', '2025-09-01', '2026-08-31', 'board9/board9.jpg', 'Viktor Petrov', 'Secretary', 'Hello fellow gamers! I''m Viktor ''AriosFury'' Petrov, this year''s Secretary of Blueshell, now in my third year of Mechanical Engineering at UT. I joined the association on a whim last year, wanting to find people in Enschede to play Valorant with. Although originally I didn''t participate in many events, I slowly found my crowd - especially by playing with the awesome BS Waterboarders - and began being at every event I had time for. Later, I joined some committees which I also found pretty fun! Blueshell gave me an amazing community and some very good friends, so I wanted to contribute to the growth of the association by becoming board. Hopefully, we''d all have a very fun year and I''m waiting to see you at the events!', 'board9/Viktor.jpg'),
('9th Board', '2025-09-01', '2026-08-31', 'board9/board9.jpg', 'Taha Aydin', 'Treasurer', 'Hi my name is Taha ''Talpa'' Aydin,
This year ill be the treasurer for this lovely association. I am 25 years old have been living most of my life in Amsterdam en Hoofddorp. A year ago I decide to move to Enschede to study Health science at the University of Twente. I started loving gaming when I was young by playing a lot of Nintendo games after that I got addicted to shooters and league of legends. I am excited to manage the finances and make it a fun year for all of us.', 'board9/Taha.jpg'),
('9th Board', '2025-09-01', '2026-08-31', 'board9/board9.jpg', 'Sylwia Siekman', 'Commissioner of Internal Affairs', 'Hi! I am Sylwia ''SylWorld'' Siekman and I will be the Commissioner of Internal Affairs this year for Blueshell. I am currently working on my 3rd year of Psychology. I have been always active around the University of Twente, from organising events to CCP. I joined Blueshell two years ago and created my own committee with a friend, SacrifiCie. Besides playing Valorant till 2am, I also play a lot of other genres so you can talk with me about anything. Furthermore, I have every month a new hyperfixation. Oh yeah, be warned, I am a yapper. Let''s have another great year!', 'board9/Sylwia.jpg'),
('9th Board', '2025-09-01', '2026-08-31', 'board9/board9.jpg', 'Boris Kusters', 'Commissioner of External Affairs', 'Hello everyone! I''m Boris ''JakobDutch'' Kusters, and I''m excited to be the Commissioner of External Affairs for the 9th Board of Blueshell. I just recently graduated from Creative Media & Game Technologies at Saxion. When I''m not busy building up my own company, Marketing Maatwerk, you can usually find me at the gym or playing CS2 with the guys from BS HyperS. I joined Blueshell about four years ago, but I truly became active after I joined HyperS. Through this community, I''ve found many new close friends, and I hope to meet a lot more of you this year!', 'board9/Boris.jpg'),
('9th Board', '2025-09-01', '2026-08-31', 'board9/board9.jpg', 'Rene Hammink', 'Commissioner of Esports Affairs', 'Hello! I am René ''Mr. Pancake^-^'' Hammink and I am proud to be the commisioner of Esports affairs of the 9th board of Blueshell. In my free time I like to develop games or do related projects and not so coinsidentally I am in my second year of Creative Media and Gaming Technologies at Saxion. Apart from playing or making videogames I like to skateboard, play DnD and ponder about philosophical questions or topics. I joined Blueshell in 2022 when I came to Enschede, mostly being interested in the Esports teams within Blueshell. Now after a few years of being a part of this awesome community I found myself more active and wanting to give back to it, and together with the rest of my boardmates I think we''ll be able to do just that!', 'board9/Rene.jpg');

-- The boards themselves. `candidate` is NOT NULL and holds the board's own name,
-- which is all the page has ever recorded for it.
INSERT INTO boards (name, candidate, start_date, end_date, image)
SELECT DISTINCT i.board_name, i.board_name, i.start_date, i.end_date, i.board_image
FROM board_import i
WHERE NOT EXISTS (SELECT 1 FROM boards b WHERE b.name = i.board_name AND b.deleted_at = '9999-12-31 23:59:59');

-- One seat per person per board, under the name the page published.
INSERT INTO board_members (board_id, user_id, display_name, description, image, role, start_date, end_date)
SELECT b.id, NULL, i.display_name, i.description, i.image, i.role, i.start_date, i.end_date
FROM board_import i
JOIN boards b ON b.name = i.board_name AND b.deleted_at = '9999-12-31 23:59:59'
WHERE NOT EXISTS (
    SELECT 1 FROM board_members m
    WHERE m.board_id = b.id AND m.display_name = i.display_name
      AND m.deleted_at = '9999-12-31 23:59:59');

DROP TEMPORARY TABLE board_import;

-- Members, where a name matches exactly one account. A name matching nobody, or more
-- than one person, leaves the seat standing under its own name for an admin to link.
UPDATE board_members m
JOIN (
    SELECT TRIM(CONCAT_WS(' ', u.first_name, u.prefix, u.last_name)) AS full_name,
           MIN(u.id) AS user_id
    FROM users u
    WHERE u.deleted_at = '9999-12-31 23:59:59.000000'
    GROUP BY full_name
    HAVING COUNT(*) = 1
) n ON n.full_name = m.display_name
SET m.user_id = n.user_id
WHERE m.user_id IS NULL AND m.display_name IS NOT NULL AND m.deleted_at = '9999-12-31 23:59:59';
