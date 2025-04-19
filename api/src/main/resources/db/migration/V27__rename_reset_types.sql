UPDATE users
    SET reset_type = 'USER_ACTIVATION'
    WHERE reset_type = 'INITIAL_ACCOUNT_CREATION';