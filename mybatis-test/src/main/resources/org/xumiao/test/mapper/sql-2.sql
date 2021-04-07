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