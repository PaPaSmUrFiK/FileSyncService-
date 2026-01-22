-- Assign ADMIN role to admin@gmail.com
DO $$
DECLARE
    admin_user_id UUID;
    admin_role_id UUID;
BEGIN
    -- Get admin user ID
    SELECT id INTO admin_user_id FROM users WHERE email = 'admin@gmail.com';
    
    -- Get ADMIN role ID
    SELECT id INTO admin_role_id FROM roles WHERE name = 'ADMIN';
    
    -- Check if both exist
    IF admin_user_id IS NULL THEN
        RAISE NOTICE 'User admin@gmail.com not found';
    ELSIF admin_role_id IS NULL THEN
        RAISE NOTICE 'Role ADMIN not found';
    ELSE
        -- Insert role assignment if not exists
        INSERT INTO user_roles (user_id, role_id)
        VALUES (admin_user_id, admin_role_id)
        ON CONFLICT DO NOTHING;
        
        RAISE NOTICE 'ADMIN role assigned to admin@gmail.com';
    END IF;
END $$;

-- Verify the assignment
SELECT u.email, r.name as role
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
WHERE u.email = 'admin@gmail.com';
