/**
 * FancyHelper Plugin Version Worker
 *
 * GET  /api/plugin/latest  — 返回最新版本信息（公开）
 * POST /api/plugin/upload  — 上传版本信息（需 Bearer token 鉴权）
 */

// UPLOAD_TOKEN 通过 wrangler secret put 设置，自 env 参数传入

// KV key
const KV_KEY = 'plugin:latest';

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    // CORS headers
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    };

    // Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    try {
      // GET /api/plugin/latest — 获取最新版本信息（公开）
      if (request.method === 'GET' && path === '/api/plugin/latest') {
        const data = await env.PLUGIN_METADATA.get(KV_KEY, 'json');
        if (!data) {
          return Response.json(
            { error: 'No version data available' },
            { status: 404, headers: corsHeaders }
          );
        }
        return Response.json(data, { headers: corsHeaders });
      }

      // POST /api/plugin/upload — 上传版本信息（需鉴权）
      if (request.method === 'POST' && path === '/api/plugin/upload') {
        // 鉴权：校验 Bearer token
        const authHeader = request.headers.get('Authorization') || '';
        const token = authHeader.replace(/^Bearer\s+/i, '');

        if (!token || token !== env.UPLOAD_TOKEN) {
          return Response.json(
            { error: 'Unauthorized' },
            { status: 401, headers: corsHeaders }
          );
        }

        // 解析请求体
        let body;
        try {
          body = await request.json();
        } catch {
          return Response.json(
            { error: 'Invalid JSON body' },
            { status: 400, headers: corsHeaders }
          );
        }

        // 校验必填字段
        const requiredFields = ['version', 'file_name', 'download_url'];
        for (const field of requiredFields) {
          if (!body[field]) {
            return Response.json(
              { error: `Missing required field: ${field}` },
              { status: 400, headers: corsHeaders }
            );
          }
        }

        // 字段校验：防止路径穿越和注入
        if (typeof body.version !== 'string' || body.version.length > 50) {
          return Response.json({ error: 'Invalid version' }, { status: 400, headers: corsHeaders });
        }
        if (typeof body.file_name !== 'string' || body.file_name.includes('..') || body.file_name.includes('/') || body.file_name.length > 200) {
          return Response.json({ error: 'Invalid file_name' }, { status: 400, headers: corsHeaders });
        }
        if (typeof body.download_url !== 'string' || !body.download_url.startsWith('https://') || body.download_url.length > 2000) {
          return Response.json({ error: 'Invalid download_url' }, { status: 400, headers: corsHeaders });
        }
        if (body.sha256 && (typeof body.sha256 !== 'string' || body.sha256.length > 128)) {
          return Response.json({ error: 'Invalid sha256' }, { status: 400, headers: corsHeaders });
        }
        if (body.changelog && typeof body.changelog !== 'string') {
          return Response.json({ error: 'Invalid changelog' }, { status: 400, headers: corsHeaders });
        }

        // 写入 KV
        const metadata = {
          version: body.version,
          file_name: body.file_name,
          download_url: body.download_url,
          sha256: body.sha256 || '',
          changelog: body.changelog || '',
          updated_at: new Date().toISOString(),
        };

        await env.PLUGIN_METADATA.put(KV_KEY, JSON.stringify(metadata));

        return Response.json(
          { success: true, version: metadata.version },
          { status: 200, headers: corsHeaders }
        );
      }

      // 404 for unknown routes
      return Response.json(
        { error: 'Not found' },
        { status: 404, headers: corsHeaders }
      );

    } catch (err) {
      return Response.json(
        { error: 'Internal server error', detail: err.message },
        { status: 500, headers: corsHeaders }
      );
    }
  },
};
