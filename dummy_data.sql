-- Dummy data for testing
-- Execute in order: resource_groups → nodes → gpus, groups, container_images

-- 1. Resource Groups
INSERT INTO resource_groups (rsgroup_id, resource_group_name, description, server_name) VALUES
(1, 'RTX 4090 Cluster', 'High-performance GPU cluster with RTX 4090 cards', 'LAB'),
(2, 'RTX 3090 Ti Cluster', 'Mid-range GPU cluster with RTX 3090 Ti cards', 'FARM'),
(3, 'A100 Server', 'Enterprise GPU server with A100 cards', 'LAB'),
(4, 'RTX 3080 Development', 'Development environment with RTX 3080 cards', 'FARM');

-- 2. Nodes
INSERT INTO nodes (node_id, rsgroup_id, memory_size_GB, CPU_core_count) VALUES
('LAB1', 1, 128, 32),
('LAB2', 1, 64, 16),
('LAB3', 3, 256, 64),
('LAB4', 3, 512, 96),
('FARM1', 2, 128, 32),
('FARM2', 2, 256, 64),
('FARM6', 4, 64, 16),
('FARM7', 4, 32, 8),
('FARM8', 2, 128, 32),
('FARM9', 4, 64, 16);

-- 3. GPUs (homogeneous per node)
INSERT INTO gpus (node_id, gpu_model, RAM_GB) VALUES
-- LAB1 (2x RTX 4090)
('LAB1', 'RTX 4090', 24),
('LAB1', 'RTX 4090', 24),
-- LAB2 (1x RTX 4090)
('LAB2', 'RTX 4090', 24),
-- LAB3 (4x A100)
('LAB3', 'A100', 80),
('LAB3', 'A100', 80),
('LAB3', 'A100', 80),
('LAB3', 'A100', 80),
-- LAB4 (8x A100)
('LAB4', 'A100', 80),
('LAB4', 'A100', 80),
('LAB4', 'A100', 80),
('LAB4', 'A100', 80),
('LAB4', 'A100', 80),
('LAB4', 'A100', 80),
('LAB4', 'A100', 80),
('LAB4', 'A100', 80),
-- FARM1 (2x RTX 3090 Ti)
('FARM1', 'RTX 3090 Ti', 24),
('FARM1', 'RTX 3090 Ti', 24),
-- FARM2 (4x RTX 3090 Ti)
('FARM2', 'RTX 3090 Ti', 24),
('FARM2', 'RTX 3090 Ti', 24),
('FARM2', 'RTX 3090 Ti', 24),
('FARM2', 'RTX 3090 Ti', 24),
-- FARM6 (2x RTX 3080)
('FARM6', 'RTX 3080', 10),
('FARM6', 'RTX 3080', 10),
-- FARM7 (1x RTX 3080)
('FARM7', 'RTX 3080', 10),
-- FARM8 (2x RTX 3090 Ti)
('FARM8', 'RTX 3090 Ti', 24),
('FARM8', 'RTX 3090 Ti', 24),
-- FARM9 (2x RTX 3080)
('FARM9', 'RTX 3080', 10),
('FARM9', 'RTX 3080', 10);

-- 4. Used IDs for groups
INSERT INTO used_ids (id_value) VALUES
(1001),
(1002),
(1003),
(1004),
(1005);

-- 5. Groups
INSERT INTO `groups` (ubuntu_gid, group_name) VALUES
(1001, 'ml-researchers'),
(1002, 'data-scientists'),
(1003, 'ai-developers'),
(1004, 'gpu-users'),
(1005, 'admin-team');

-- 6. Container Images (CUDA versions only)
INSERT INTO container_image (image_name, image_version, cuda_version, description, created_at, updated_at) VALUES
('cuda', '11.8', '11.8', 'CUDA 11.8 development environment', NOW(), NOW()),
('cuda', '12.0', '12.0', 'CUDA 12.0 development environment', NOW(), NOW()),
('cuda', '11.7', '11.7', 'CUDA 11.7 development environment', NOW(), NOW()),
('cuda', '12.1', '12.1', 'CUDA 12.1 development environment', NOW(), NOW()),
('cuda', '11.6', '11.6', 'CUDA 11.6 development environment', NOW(), NOW()),
('cuda', '12.2', '12.2', 'CUDA 12.2 development environment', NOW(), NOW()),
('cuda', '11.5', '11.5', 'CUDA 11.5 development environment', NOW(), NOW()),
('cuda', '12.3', '12.3', 'CUDA 12.3 development environment', NOW(), NOW());