create table guests
(
    id         bigint not null auto_increment,
    name       text   not null,
    discord    text,
    email      text   not null,
    created_at datetime default NOW(),
    primary key (id)
);

alter table event_signups
    drop primary key,
    add column id       int    not null auto_increment primary key first,

    add constraint event_signups_user_id_foreign
        foreign key (user_id) references users (id),

    add column guest_id bigint null after user_id,
    add constraint event_signups_guest_id_foreign
        foreign key (guest_id) references guests (id),

    modify user_id bigint null,
    add constraint event_signups_user_id_guest_id_one_or_the_other
        check ( user_id is null xor guest_id is null ),

    add constraint event_signups_event_id_user_id_guest_id_unique
        unique (event_id, user_id, guest_id);

