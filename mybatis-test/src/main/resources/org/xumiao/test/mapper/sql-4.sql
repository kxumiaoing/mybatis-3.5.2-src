CREATE TABLE `person` (
    `id` smallint(5) unsigned NOT NULL AUTO_INCREMENT,
    `name` char(60) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB;


CREATE TABLE `teacher` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `name` varchar(40) DEFAULT NULL,
    `age` tinyint(3) unsigned DEFAULT '0',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB;


CREATE TABLE `children` (
    `id` smallint(5) unsigned NOT NULL AUTO_INCREMENT,
    `name` char(60) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB;


CREATE TABLE `rel_person_teacher` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `person_id` int(11) NOT NULL,
    `teacher_id` int(11) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `person_id` (`person_id`,`teacher_id`)
) ENGINE=InnoDB;


CREATE TABLE `rel_person_children` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `person_id` int(11) DEFAULT NULL,
    `children_id` int(11) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `person_id` (`person_id`,`children_id`)
) ENGINE=InnoDB;


delimiter $$
CREATE PROCEDURE `get_teacher_name_and_children_name_by_id`(
    in person_id int,
    out teacher_name varchar(256),
    out children_name varchar(256))
begin
    select t.name into teacher_name
    from teacher t
    where t.id = (select r.teacher_id
    from rel_person_teacher r
    where r.person_id = person_id);

    select c.name into children_name
    from children c
    where c.id = (select r.children_id
    from rel_person_children r
    where r.person_id = person_id);
end $$

delimiter ;