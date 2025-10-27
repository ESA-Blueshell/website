CREATE DATABASE IF NOT EXISTS blueshell;
CREATE DATABASE IF NOT EXISTS `blueshell-test`;

GRANT ALL PRIVILEGES ON blueshell.* TO 'blueshell'@'%';
GRANT ALL PRIVILEGES ON `blueshell-test`.* TO 'blueshell'@'%';
FLUSH PRIVILEGES;