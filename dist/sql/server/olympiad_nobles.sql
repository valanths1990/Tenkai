CREATE TABLE IF NOT EXISTS `olympiad_nobles` (
  `charId` INT UNSIGNED NOT NULL default 0,
  `class_id` decimal(3,0) NOT NULL default 0,
  `olympiad_points` decimal(10,0) NOT NULL default 0,
  `competitions_done` decimal(3,0) NOT NULL default 0,
  `competitions_won` decimal(3,0) NOT NULL default 0,
  `competitions_lost` decimal(3,0) NOT NULL default 0,
  `competitions_drawn` decimal(3,0) NOT NULL default 0,
  `competitions_classed` decimal(3,0) NOT NULL default 0,
  `competitions_nonclassed` decimal(3,0) NOT NULL default 0,
  `competitions_teams` decimal(3,0) NOT NULL default 0,
  `settled` int(1) NOT NULL default 0,
  PRIMARY KEY (`charId`)
);