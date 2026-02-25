# Stack Overflow Search Engine

A modern search engine for Stack Overflow questions powered by AI. Get instant answers with AI-generated overviews of the most relevant coding solutions.

## Features

- **Fast Search**: Semantic search across millions of Stack Overflow questions
- **AI Overview**: AI-powered summaries of the best answers using Mistral AI
- **Smart Ranking**: Results ranked by relevance, freshness, and community votes
- **Smart Caching**: Redis-based caching for instant repeat searches
- **Advanced Filtering**: Filter by tags, sort by relevance or date

## Tech Stack

- **Backend**: Spring Boot 3.x (Java 17+)
- **Cache**: Redis
- **Database**: PostgreSQL
- **LLM**: Mistral AI
- **APIs**: Stack Exchange API
- **Frontend**: Vanilla JavaScript, modern CSS

## Quick Start

### Requirements

- Java 17+
- Docker & Docker Compose

### Setup

1. Clone the repository and navigate to the project directory
2. Create a `.env` file with your Stack Exchange API key:

```bash
STACKEXCHANGE_API_KEY=your_key_here
```

3. Start the application with Docker:

```bash
docker compose up -d
```

4. Open your browser and visit `http://localhost:8080`

## Development

### Run with Maven

```bash
./mvnw spring-boot:run
```

### Test

```bash
mvn test
```

## API Endpoints

- `GET /api/search` - Search Stack Overflow questions
- `GET /api/health` - Health check
- `GET /api/analytics` - Search analytics

Example:

```bash
curl "http://localhost:8080/api/search?q=spring%20boot%20error"
```

## License

Built by Dat
