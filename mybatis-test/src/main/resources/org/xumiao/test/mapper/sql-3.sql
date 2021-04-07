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


CREATE TABLE `rel_person_teacher` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `person_id` int(11) NOT NULL,
    `teacher_id` int(11) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `person_id` (`person_id`,`teacher_id`)
) ENGINE=InnoDB;


delimiter $$

CREATE PROCEDURE `get_person_and_teacher_by_id`(in id int)
begin
    select p.id, p.name,p.id as person_id
    from person p
    where p.id = id;

    select t.id, t.name,t.age
    from teacher t
    where t.id in (select distinct r.teacher_id
    from rel_person_teacher r
    where r.person_id = id);
end $$

delimiter ;