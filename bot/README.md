# Discord Member Bot

A simple read-only Discord bot that provides an API to fetch member information from your Discord server.

## Key Features:
1. **Read-only access** - Only fetches data, cannot modify anything
2. **Member search** - Search by username or display name
3. **Role information** - Gets member roles and permissions
4. **Caching** - Built-in caching to reduce API calls
5. **RESTful API** - Easy integration with your frontend
6. **Security** - Optional API key authentication
7. **Docker support** - Easy deployment

## API Endpoints:
- `GET /api/members/search?q=username` - Search for members
- `GET /api/members/:userId` - Get specific member by Discord ID
- `GET /api/members` - Get all members
- `GET /api/roles` - Get all server roles

To set this up:

1. Create the `discord_bot` folder in your project root
2. Add all the files above
3. Run `npm install` in the discord_bot directory
4. Create a Discord bot in the Developer Portal
5. Configure your `.env` file
6. Add the bot to your Discord server
7. Run the bot with `npm start`
