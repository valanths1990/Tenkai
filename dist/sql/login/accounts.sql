CREATE TABLE IF NOT EXISTS `accounts` (
  `login` VARCHAR(45) NOT NULL default '',
  `password` VARCHAR(100) ,
  `lastactive` bigint(13) unsigned NOT NULL DEFAULT '0',
  `accessLevel` TINYINT NOT NULL DEFAULT 0,
  `lastIP` VARCHAR(15) NULL DEFAULT NULL,
  `lastIP2` VARCHAR(15) NULL DEFAULT NULL,
  `lastIP3` VARCHAR(15) NULL DEFAULT NULL,
  `lastServer` TINYINT DEFAULT 1,
  `userIP` char(15) DEFAULT NULL,
  `pcIp` char(15) DEFAULT NULL,
  `hop1` char(15) DEFAULT NULL,
  `hop2` char(15) DEFAULT NULL,
  `hop3` char(15) DEFAULT NULL,
  `hop4` char(15) DEFAULT NULL,
  `site` VARCHAR(45) NOT NULL default '',
  PRIMARY KEY (`login`)
);