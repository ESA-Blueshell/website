set FOREIGN_KEY_CHECKS = 0;
ALTER SCHEMA `API`  DEFAULT CHARACTER SET utf8  DEFAULT COLLATE utf8_general_ci ;

drop table if exists
  pictures,
  users,
  events,
  event_signups,
  billables,
  event_feedback,
  sponsors,
  committees,
  committee_members,
  subscriptions,
  roles,
  registrations,
  news,
  authorities
;

set FOREIGN_KEY_CHECKS = 1;


create table users
(
  id                bigint   not null auto_increment,
  username          text,
  password          text     not null,
  first_name        text     not null,
  last_name         text     not null,
  prefix            text,
  initials          text,
  address           text,
  house_number      text,
  postal_code       text,
  city              text,
  phone_number      text,
  email             text     not null,
  student_number    text,
  date_of_birth     datetime,
  created_at        datetime not null,
  member_since      datetime not null,
  discord           text,  #TODO: change to discordid / discordtag ALSO CHANGE IT IN FRONTEND THEN (on account page for example)
  steamid           text,
  newsletter        bool     not null,
  contribution_paid bool     not null,
  consent_privacy   bool     not null,
  consent_gdpr      bool     not null,
  registration_id   bigint, # registration
  profile_picture   bigint, # possible picture
  deleted_at        datetime,
  enabled           boolean  default false,
  primary key (id)
);

create table committees
(
  id          bigint not null auto_increment,
  name        text,
  description text,
  primary key (id)
);

create table pictures
(
  id          bigint not null auto_increment,
  name        varchar(255),
  url         varchar(255),
  created_at  datetime,
  uploader_id bigint,
  event_id    bigint,
  primary key (id),
  foreign key (uploader_id) references users (id)
);

create table events
(
  id             bigint       not null auto_increment,
  creator_id     bigint,                # user
  last_editor_id bigint,                # user
  committee_id   bigint,                # committee
  title          varchar(255) not null,
  description    varchar(255) not null,
  location       varchar(255),
  start_time     datetime,
  banner_id      bigint,                # promo pic
  price_member   double,
  price_public   double,
  visible        bool not null,
  members_only   bool,
  sign_up        bool not null,
  sign_up_form   json,
  max_participants bigint,
  primary key (id),
  foreign key (creator_id) references users (id),
  foreign key (last_editor_id) references users (id),
  foreign key (committee_id) references committees (id)
);

create table event_signups
(
  event_id     bigint,
  user_id      bigint,
  options      text,
  signed_up_at datetime,
  primary key (event_id, user_id)
);

create table billables
(
  id          bigint not null auto_increment,
  source_id   bigint, # user
  description text,
  quantity    int,
  price       double,
  paid        bool,
  event_id    bigint, # possible event
  created_at  datetime,
  primary key (id),
  foreign key (source_id) references users (id)
);

create table event_feedback
(
  id       bigint not null auto_increment,
  feedback text,
  event_id bigint, # event
  primary key (id),
  foreign key (event_id) references events (id)
);

create table sponsors
(
  id          bigint not null auto_increment,
  name        text,
  description text,
  logo_id     bigint, # possible picture
  primary key (id)
);

create table committee_members
(
  user_id      bigint,
  committee_id bigint,
  role         text,
  primary key (user_id, committee_id)
);

create table subscriptions
(
  user_id      bigint,
  committee_id bigint,
  primary key (user_id, committee_id)
);

create table roles
(
  user_id bigint,
  role    varchar(255), # enum
  primary key (user_id, role)
);

create table registrations
(
  id                  bigint not null auto_increment,
  user_id             bigint,
  registration_state  text,   # enum
  accepted_by_user_id bigint, # user
  accepted_at         datetime,
  primary key (id),
  foreign key (user_id) references users (id),
  foreign key (accepted_by_user_id) references users (id)
);

create table news
(
id             bigint not null auto_increment,
creator_id     bigint,                  # user
last_editor_id bigint,                # user
news_type      varchar(255) not null,
title          varchar(255) not null,
content        text not null,
posted_at      datetime,
primary key (id),
foreign key (creator_id) references users (id),
foreign key (last_editor_id) references users (id)
);

create table authorities
(
  user_id   bigint,       # for
  authority varchar(255), # same
  primary key (user_id, authority)
);

insert into users (username, enabled, password, first_name, last_name, email, created_at, member_since, newsletter,
                   contribution_paid, consent_privacy, consent_gdpr)
values
       ('admin', true, '$2a$10$cwKSYweW60.FIJf8rR40.e8t3706g4ReEDEXAYmxX16oXkWfdVSba', 'ad', 'min', 'ad@min', NOW(), NOW(), true, true, true, true),
       ('user', true, '$2a$10$/qL7UwPKq0qeAoQDrQ2k2egdk7ldDroa50CPNmf6nud7F4QOGm3S6', 'u', 'ser', 'us@er', NOW(), NOW(), true, true, true, true),
       ('board', true, '$2a$10$/qL7UwPKq0qeAoQDrQ2k2egdk7ldDroa50CPNmf6nud7F4QOGm3S6', 'u', 'ser', 'us@er', NOW(), NOW(), true, true, true, true),
       ('treasurer', true, '$2a$10$/qL7UwPKq0qeAoQDrQ2k2egdk7ldDroa50CPNmf6nud7F4QOGm3S6', 'u', 'ser', 'us@er', NOW(), NOW(), true, true, true, true),
       ('guest', true, '$2a$10$/qL7UwPKq0qeAoQDrQ2k2egdk7ldDroa50CPNmf6nud7F4QOGm3S6', 'u', 'ser', 'us@er', NOW(), NOW(), true, true, true, true),
       ('Louis Hu', true, 'redacted', 'Louis', 'Hu', 'lou@uis', NOW(), NOW(), true, true, true, true);

insert into authorities (user_id, authority)
values (1, 'ADMIN'),
       (2, 'MEMBER'),
       (3, 'BOARD'),
       (4, 'TREASURER'),
       (5, 'GUEST');
