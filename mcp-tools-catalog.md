# HYEEE MCP Tools Catalog

This document is the tool contract for the Java MCP server.

Endpoint:

```text
POST http://127.0.0.1:8081/mcp
```

Authentication:

```http
Authorization: Bearer ${MCP_SERVER_TOKEN}
```

## Registered Tools

The following tools are already registered by the Java service and returned by `tools/list`.

```json
{
  "tools": [
    {
      "name": "search_shops",
      "description": "Search shops by keyword or category with optional coordinates.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "keyword": { "type": "string", "description": "Shop name keyword" },
          "type_id": { "type": "integer", "description": "Shop category ID" },
          "page": { "type": "integer", "description": "Page number, starting from 1" },
          "longitude": { "type": "number", "description": "Longitude for distance sorting" },
          "latitude": { "type": "number", "description": "Latitude for distance sorting" }
        },
        "required": [],
        "additionalProperties": false
      }
    },
    {
      "name": "get_shop_detail",
      "description": "Get a shop by ID.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "shop_id": { "type": "integer", "description": "Shop ID" }
        },
        "required": ["shop_id"],
        "additionalProperties": false
      }
    },
    {
      "name": "list_shop_types",
      "description": "List all shop categories in display order.",
      "inputSchema": {
        "type": "object",
        "properties": {},
        "required": [],
        "additionalProperties": false
      }
    },
    {
      "name": "list_shop_vouchers",
      "description": "List available vouchers for a shop. This is a shop voucher catalog, not a user's coupon balance.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "shop_id": { "type": "integer", "description": "Shop ID" }
        },
        "required": ["shop_id"],
        "additionalProperties": false
      }
    },
    {
      "name": "list_hot_blogs",
      "description": "List popular local-life posts.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "page": { "type": "integer", "description": "Page number, starting from 1" }
        },
        "required": [],
        "additionalProperties": false
      }
    },
    {
      "name": "list_hot_topics",
      "description": "List the realtime topic ranking.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "limit": { "type": "integer", "description": "Number of topics, from 1 to 50" }
        },
        "required": [],
        "additionalProperties": false
      }
    },
    {
      "name": "list_topic_blogs",
      "description": "List posts associated with a topic.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "topic_id": { "type": "integer", "description": "Topic ID" },
          "page": { "type": "integer", "description": "Page number, starting from 1" }
        },
        "required": ["topic_id"],
        "additionalProperties": false
      }
    }
  ]
}
```

## Candidate Query Tools (Not Registered)

The following capabilities already exist in the project but are not exposed through MCP yet.

```json
{
  "tools": [
    {
      "name": "get_blog_detail",
      "description": "Get one blog post and its public details.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "blog_id": { "type": "integer", "description": "Blog ID" }
        },
        "required": ["blog_id"],
        "additionalProperties": false
      }
    },
    {
      "name": "list_blog_comments",
      "description": "List comments for one blog post.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "blog_id": { "type": "integer", "description": "Blog ID" }
        },
        "required": ["blog_id"],
        "additionalProperties": false
      }
    },
    {
      "name": "get_my_profile",
      "description": "Get the authenticated user's profile. Requires a trusted user identity context.",
      "inputSchema": {
        "type": "object",
        "properties": {},
        "required": [],
        "additionalProperties": false
      }
    },
    {
      "name": "list_my_blogs",
      "description": "List blog posts published by the authenticated user. Requires a trusted user identity context.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "page": { "type": "integer", "description": "Page number, starting from 1" }
        },
        "required": [],
        "additionalProperties": false
      }
    },
    {
      "name": "get_follow_status",
      "description": "Check whether the authenticated user follows another user. Requires a trusted user identity context.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "target_user_id": { "type": "integer", "description": "Target user ID" }
        },
        "required": ["target_user_id"],
        "additionalProperties": false
      }
    }
  ]
}
```

## Candidate Write Tools (Not Registered)

These tools must not be registered until the MCP server has trusted user identity propagation, confirmation handling, audit logging, idempotency, and rate limiting.

```json
{
  "tools": [
    {
      "name": "create_blog",
      "description": "Publish a blog post for the authenticated user. Requires explicit user confirmation.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "title": { "type": "string", "description": "Blog title" },
          "content": { "type": "string", "description": "Blog content" },
          "images": { "type": "array", "items": { "type": "string" }, "description": "Uploaded image URLs" },
          "shop_id": { "type": "integer", "description": "Optional associated shop ID" }
        },
        "required": ["title", "content", "images"],
        "additionalProperties": false
      }
    },
    {
      "name": "like_blog",
      "description": "Like or unlike a blog post for the authenticated user. Requires explicit user confirmation.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "blog_id": { "type": "integer", "description": "Blog ID" },
          "action": { "type": "string", "enum": ["like", "unlike"], "description": "Requested like action" }
        },
        "required": ["blog_id", "action"],
        "additionalProperties": false
      }
    },
    {
      "name": "create_blog_comment",
      "description": "Publish a comment for the authenticated user. Requires explicit user confirmation and content moderation.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "blog_id": { "type": "integer", "description": "Blog ID" },
          "content": { "type": "string", "description": "Comment content" },
          "parent_comment_id": { "type": "integer", "description": "Optional parent comment ID" }
        },
        "required": ["blog_id", "content"],
        "additionalProperties": false
      }
    },
    {
      "name": "set_follow_status",
      "description": "Follow or unfollow a user for the authenticated user. Requires explicit user confirmation.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "target_user_id": { "type": "integer", "description": "Target user ID" },
          "following": { "type": "boolean", "description": "True to follow, false to unfollow" }
        },
        "required": ["target_user_id", "following"],
        "additionalProperties": false
      }
    },
    {
      "name": "sign_in_today",
      "description": "Record today's check-in for the authenticated user. Requires explicit user confirmation and idempotency protection.",
      "inputSchema": {
        "type": "object",
        "properties": {},
        "required": [],
        "additionalProperties": false
      }
    },
    {
      "name": "seckill_voucher_order",
      "description": "Create a flash-sale voucher order. Do not register until payment or order confirmation, inventory protection, idempotency, and rate limiting are implemented.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "voucher_id": { "type": "integer", "description": "Seckill voucher ID" }
        },
        "required": ["voucher_id"],
        "additionalProperties": false
      }
    }
  ]
}
```

## Not Available In This Project

The following requested business tools cannot be registered until their domain services are built:

```json
[
  "order_query",
  "logistics_query",
  "refund_query",
  "coupon_balance_query"
]
```
