/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {useCallback, useState} from 'react';
import type {TablePaginationConfig} from 'antd/es/table/interface';

interface PageInfoLike {
    totalElements?: number;
    size?: number;
    number?: number;
}

interface UseTablePaginationOptions {
    defaultPageSize?: number;
    totalLabel?: string;
}

interface TablePaginationOptions {
    pageSizeOptions?: string[];
}

export const useTablePagination = ({
    defaultPageSize = 10,
    totalLabel = 'Total',
}: UseTablePaginationOptions = {}) => {
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(defaultPageSize);
    const [total, setTotal] = useState(0);

    const applyPageInfo = useCallback((pageInfo: PageInfoLike, requestedPage: number, requestedPageSize: number) => {
        setCurrentPage((pageInfo.number ?? requestedPage - 1) + 1);
        setPageSize(pageInfo.size ?? requestedPageSize);
        setTotal(pageInfo.totalElements ?? 0);
    }, []);

    const getPageAfterDelete = useCallback((deletedCount: number = 1) => {
        const nextTotal = Math.max(0, total - deletedCount);
        return Math.min(currentPage, Math.max(1, Math.ceil(nextTotal / pageSize)));
    }, [currentPage, pageSize, total]);

    const getTablePagination = useCallback((
        onPageChange: (page: number, size: number) => void,
        options: TablePaginationOptions = {},
    ): TablePaginationConfig => ({
        current: currentPage,
        pageSize,
        total,
        showSizeChanger: true,
        showQuickJumper: true,
        showTotal: count => `${totalLabel} ${count}`,
        pageSizeOptions: options.pageSizeOptions,
        onChange: onPageChange,
        onShowSizeChange: (_current, size) => onPageChange(1, size),
    }), [currentPage, pageSize, total, totalLabel]);

    return {
        currentPage,
        pageSize,
        total,
        applyPageInfo,
        getPageAfterDelete,
        getTablePagination,
    };
};
