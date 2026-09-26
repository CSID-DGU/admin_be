-- 기준 스키마(2026-09-27). 이 파일 이전의 변경은 migrations/(수동 실행 기록)에 있다.
-- 네 스택(operation·full·baseline·noprobe)의 web_admin 스키마를 맞춘 뒤 운영에서 그대로 떠 왔다.
--
-- 이미 표가 있는 DB는 spring.flyway.baseline-on-migrate로 이 버전을 "적용됨"으로만 기록하고 실행하지 않는다.
-- 빈 DB(새 스택)에서만 실행된다. 이후 스키마 변경은 V2__... 부터 새 파일로 추가하고, 이 파일은 고치지 않는다.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `change_request` (
  `change_request_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `admin_comment` varchar(500) DEFAULT NULL,
  `change_type` enum('CONTAINER_IMAGE','EXPIRES_AT','GROUP','PORT','RESOURCE_GROUP') NOT NULL,
  `new_value` json NOT NULL,
  `old_value` json DEFAULT NULL,
  `reason` varchar(1000) NOT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `status` enum('DELETED','DENIED','EXPIRING','FULFILLED','MIGRATING','PENDING','PROCESSING') NOT NULL,
  `request_id` bigint NOT NULL,
  `requested_by` bigint NOT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  PRIMARY KEY (`change_request_id`),
  KEY `FKejtrjle25dny01rk2j55g7i26` (`request_id`),
  KEY `FKcf6sokg9vpi29xtxfyrr3b5go` (`requested_by`),
  KEY `FKkst9vehntddtga6cj5fst5h9l` (`reviewed_by`),
  CONSTRAINT `FKcf6sokg9vpi29xtxfyrr3b5go` FOREIGN KEY (`requested_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKejtrjle25dny01rk2j55g7i26` FOREIGN KEY (`request_id`) REFERENCES `requests` (`request_id`),
  CONSTRAINT `FKkst9vehntddtga6cj5fst5h9l` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `container_image` (
  `image_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `cuda_version` varchar(100) NOT NULL,
  `description` varchar(500) NOT NULL,
  `image_name` varchar(100) NOT NULL,
  `image_version` varchar(100) NOT NULL,
  PRIMARY KEY (`image_id`),
  UNIQUE KEY `uk_container_image_name_version` (`image_name`,`image_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `gpus` (
  `gpu_id` bigint NOT NULL AUTO_INCREMENT,
  `gpu_model` varchar(100) NOT NULL,
  `ram_gb` int NOT NULL,
  `node_id` varchar(100) NOT NULL,
  PRIMARY KEY (`gpu_id`),
  KEY `FKa4wiojl42sw4j4mkywl0xxk63` (`node_id`),
  CONSTRAINT `FKa4wiojl42sw4j4mkywl0xxk63` FOREIGN KEY (`node_id`) REFERENCES `nodes` (`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `groups` (
  `group_id` bigint NOT NULL AUTO_INCREMENT,
  `group_name` varchar(100) NOT NULL,
  `ubuntu_gid` bigint NOT NULL,
  PRIMARY KEY (`group_id`),
  UNIQUE KEY `UKbp3wo757irwcvou1crjw2i5j5` (`group_name`),
  UNIQUE KEY `UK5e1idlrilrfmh6ids7omvjedf` (`ubuntu_gid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `message_templates` (
  `template_key` varchar(100) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `template_value` text NOT NULL,
  PRIMARY KEY (`template_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `nodes` (
  `node_id` varchar(100) NOT NULL,
  `cpu_core_count` int NOT NULL,
  `memory_size_gb` int NOT NULL,
  `rsgroup_id` int NOT NULL,
  PRIMARY KEY (`node_id`),
  KEY `FKtq4n1vh0tea7ef723pgb3tem` (`rsgroup_id`),
  CONSTRAINT `FKtq4n1vh0tea7ef723pgb3tem` FOREIGN KEY (`rsgroup_id`) REFERENCES `resource_groups` (`rsgroup_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `pod_external_ports` (
  `pod_external_port_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `external_port` int NOT NULL,
  `internal_port` int NOT NULL,
  `usage_purpose` varchar(1000) NOT NULL,
  `request_id` bigint NOT NULL,
  PRIMARY KEY (`pod_external_port_id`),
  KEY `FK9gtm2t375hnivpniyfc7lgrpp` (`request_id`),
  CONSTRAINT `FK9gtm2t375hnivpniyfc7lgrpp` FOREIGN KEY (`request_id`) REFERENCES `requests` (`request_id`),
  CONSTRAINT `pod_external_ports_chk_1` CHECK (((`external_port` <= 65535) and (`external_port` >= 1))),
  CONSTRAINT `pod_external_ports_chk_2` CHECK (((`internal_port` <= 65535) and (`internal_port` >= 1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `port_requests` (
  `port_request_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `internal_port` int NOT NULL,
  `is_active` bit(1) NOT NULL,
  `usage_purpose` varchar(1000) NOT NULL,
  `request_id` bigint NOT NULL,
  `rsgroup_id` int NOT NULL,
  PRIMARY KEY (`port_request_id`),
  KEY `FKdwhu6gmmp8bvonpfjx19k7h0s` (`request_id`),
  KEY `FKgeb8r4g5oq62ynpav82h7rgx8` (`rsgroup_id`),
  CONSTRAINT `FKdwhu6gmmp8bvonpfjx19k7h0s` FOREIGN KEY (`request_id`) REFERENCES `requests` (`request_id`),
  CONSTRAINT `FKgeb8r4g5oq62ynpav82h7rgx8` FOREIGN KEY (`rsgroup_id`) REFERENCES `resource_groups` (`rsgroup_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `request_groups` (
  `created_at` datetime(6) NOT NULL,
  `group_id` bigint NOT NULL,
  `request_id` bigint NOT NULL,
  PRIMARY KEY (`request_id`,`group_id`),
  KEY `FK7ilc04tvpi25hxp52y6t19qjp` (`group_id`),
  CONSTRAINT `FK1053dkbewdadeav814rfd6h74` FOREIGN KEY (`request_id`) REFERENCES `requests` (`request_id`),
  CONSTRAINT `FK7ilc04tvpi25hxp52y6t19qjp` FOREIGN KEY (`group_id`) REFERENCES `groups` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `requests` (
  `request_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `admin_comment` varchar(300) DEFAULT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `form_answers` json NOT NULL,
  `node_name` varchar(100) DEFAULT NULL,
  `pod_name` varchar(255) DEFAULT NULL,
  `status` enum('DELETED','DENIED','EXPIRING','FULFILLED','MIGRATING','PENDING','PROCESSING') NOT NULL,
  `usage_purpose` varchar(1000) NOT NULL,
  `image_id` bigint NOT NULL,
  `rsgroup_id` int NOT NULL,
  `user_id` bigint NOT NULL,
  `enable_vnc` bit(1) NOT NULL,
  `job_id` bigint DEFAULT NULL,
  PRIMARY KEY (`request_id`),
  KEY `FKh1l4rurq5s8m0agsqkl37apvd` (`image_id`),
  KEY `FK2pce39taocbtvy2came3ek2lf` (`rsgroup_id`),
  KEY `FK8usbpx9csc6opbjg1d7kvtf8c` (`user_id`),
  CONSTRAINT `FK2pce39taocbtvy2came3ek2lf` FOREIGN KEY (`rsgroup_id`) REFERENCES `resource_groups` (`rsgroup_id`),
  CONSTRAINT `FK8usbpx9csc6opbjg1d7kvtf8c` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKh1l4rurq5s8m0agsqkl37apvd` FOREIGN KEY (`image_id`) REFERENCES `container_image` (`image_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `resource_group_images` (
  `image_id` bigint NOT NULL,
  `rsgroup_id` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`image_id`,`rsgroup_id`),
  KEY `FKnjoox0ee866492cjgc6wt60au` (`rsgroup_id`),
  CONSTRAINT `FKi781obytfvau26n0nl1vggf80` FOREIGN KEY (`image_id`) REFERENCES `container_image` (`image_id`),
  CONSTRAINT `FKnjoox0ee866492cjgc6wt60au` FOREIGN KEY (`rsgroup_id`) REFERENCES `resource_groups` (`rsgroup_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `resource_groups` (
  `rsgroup_id` int NOT NULL AUTO_INCREMENT,
  `description` varchar(500) DEFAULT NULL,
  `resource_group_name` varchar(300) DEFAULT NULL,
  `server_name` varchar(300) DEFAULT NULL,
  PRIMARY KEY (`rsgroup_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `user_groups` (
  `user_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`user_id`,`group_id`),
  KEY `fk_user_groups_group` (`group_id`),
  CONSTRAINT `fk_user_groups_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`group_id`),
  CONSTRAINT `fk_user_groups_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `users` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `department` varchar(100) NOT NULL,
  `email` varchar(100) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `phone` varchar(100) NOT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  `student_id` varchar(100) NOT NULL,
  `ubuntu_gid` bigint DEFAULT NULL,
  `ubuntu_uid` bigint DEFAULT NULL,
  `ubuntu_username` varchar(100) DEFAULT NULL,
  `ubuntu_password_hash` varchar(255) DEFAULT NULL,
  `ubuntu_account_status` varchar(20) NOT NULL DEFAULT 'NONE',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `uk_users_ubuntu_username` (`ubuntu_username`),
  UNIQUE KEY `uk_users_ubuntu_uid` (`ubuntu_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
