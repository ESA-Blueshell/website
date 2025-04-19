# Blueshell backend setup:

1. Clone the blueshell-api repository from https://gitlab.com/swordiemen/blueshell-api

2. Setup a local mysql server (Up until the "Testing your Local SQL Server"), make sure to remember the username/password
https://ladvien.com/data-analytics-mysql-localhost-setup/

3. Create a schema named "blueshell"

4. Double click the created schema

5. Open the 2 files in the sql folder of the cloned repo (V0__Init.sql, auth_data.sql) and run them

6. Open the blueshell-api project in IntelliJ

7. Change the "hibernate.connection.password" and "hibernate.connection.password" properties in hibernate.cfg.xml to match the ones you set while installing the server

8. Run the "ApiApplication" file.

9. Test if everything works by going to http://localhost:8080/api/login

10. ???

11. Profit!