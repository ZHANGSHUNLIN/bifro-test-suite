import {envConfig} from '../config/env';

// 通用请求函数
export const request = async <T>(
    endpoint: string,
    {headers, params, ...customConfig}: RequestInit & { params?: Record<string, string | number | boolean> } = {}
): Promise<T> => {
    // 处理路径参数
    let url = endpoint;
    console.log('params',params)
    // 如果有参数映射，替换URL中的占位符
    if (params) {
        Object.entries(params).forEach(([key, value]) => {
            url = url.replace(`:${key}`, encodeURIComponent(String(value)));
        });
    }

    // 处理查询参数（非路径参数）
    const searchParams = new URLSearchParams();
    if (params && customConfig.method === 'GET') {
        Object.entries(params).forEach(([key, value]) => {
            if (!url.includes(`:${key}`)) {
                searchParams.append(key, String(value));
            }
        });
    }

    const queryString = searchParams.toString();
    if (queryString) {
        url += (url.includes('?') ? '&' : '?') + queryString;
    }

    // 添加基础URL
    if (!url.startsWith('http')) {
        url = `${envConfig.apiBaseUrl}${url}`;
    }

    const config: RequestInit = {
        method: 'GET',
        ...customConfig,
        headers: {
            'Content-Type': 'application/json',
            ...headers,
        },
    };

    try {
        const response = await fetch(url, config);

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        // 204 No Content 时不解析 JSON
        if (response.status === 204) {
            return {} as T;
        }

        const result = await response.json();

        // 解析 ApiResponse 格式
        if (result && typeof result === 'object' && 'code' in result) {
            if (result.code === 200) {
                return result.data as T;
            } else {
                throw new Error(result.message || `API error! code: ${result.code}`);
            }
        }

        // 非 ApiResponse 格式直接返回
        return result as T;
    } catch (error) {
        console.error(`API request failed: ${url}`, error);
        throw error;
    }
};

// 更优雅的请求方法封装
interface RequestOptions extends RequestInit {
    params?: Record<string, string | number | boolean>;
}

export const api = {
    // GET 请求
    get: <T>(url: string, options?: RequestOptions) => request<T>(url, {...options, method: 'GET'}),

    // POST 请求
    post: <T>(url: string, data?: any, options?: RequestOptions) => request<T>(url, {
        ...options,
        method: 'POST',
        body: data ? JSON.stringify(data) : undefined
    }),

    // PUT 请求
    put: <T>(url: string, data?: any, options?: RequestOptions) => request<T>(url, {
        ...options,
        method: 'PUT',
        body: data ? JSON.stringify(data) : undefined
    }),

    // DELETE 请求
    delete: <T>(url: string, data?: any, options?: RequestOptions) => request<T>(url, {
        ...options,
        method: 'DELETE',
        body: data ? JSON.stringify(data) : undefined
    }),

    // PATCH 请求
    patch: <T>(url: string, data?: any, options?: RequestOptions) => request<T>(url, {
        ...options,
        method: 'PATCH',
        body: data ? JSON.stringify(data) : undefined
    })
};