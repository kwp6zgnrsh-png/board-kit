import {createRouter, createWebHistory} from 'vue-router'

import {getAuthSession} from '@/features/auth/services/authSession'
import AdminBoardCreateView from '@/views/AdminBoardCreateView.vue'
import LoginView from '@/views/LoginView.vue'
import BoardDetailView from '@/views/BoardDetailView.vue'
import PostCreateView from '@/views/PostCreateView.vue'
import PostDetailView from '@/views/PostDetailView.vue'
import HomeView from '@/views/HomeView.vue'
import MemberCreateView from '@/views/MemberCreateView.vue'
import PostEditView from '@/views/PostEditView.vue'
import AdminBoardEditView from '@/views/AdminBoardEditView.vue'
import AdminDashboardView from '@/views/AdminDashboardView.vue'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'home',
            component: HomeView,
        },
        {
            path: '/login',
            name: 'login',
            component: LoginView,
        },
        {
            path: '/admin',
            name: 'admin-dashboard',
            component: AdminDashboardView,
            meta: {
                requiresAuth: true,
                requiredRole: 'ADMIN',
            },
        },
        {
            path: '/admin/boards/new',
            name: 'admin-board-create',
            component: AdminBoardCreateView,
            meta: {
                requiresAuth: true,
                requiredRole: 'ADMIN',
            },
        },
        {
            path: '/admin/boards/:boardSlug/edit',
            name: 'admin-board-edit',
            component: AdminBoardEditView,
            meta: {
                requiresAuth: true,
                requiredRole: 'ADMIN',
            },
        },
        {
            path: '/boards/:boardSlug',
            name: 'board-detail',
            component: BoardDetailView,
        },
        {
            path: '/boards/:boardSlug/posts/new',
            name: 'post-create',
            component: PostCreateView,
            meta: {
                requiresAuth: true,
            },
        },
        {
            path: '/boards/:boardSlug/posts/:postId',
            name: 'post-detail',
            component: PostDetailView,
        },
        {
            path: '/signup',
            name: 'member-create',
            component: MemberCreateView,
        },
        {
            path: '/boards/:boardSlug/posts/:postId/edit',
            name: 'post-edit',
            component: PostEditView,
            meta: {requiresAuth: true},
        },
        {
            path: '/me',
            name: 'my-page',
            component: () => import('@/views/MyPageView.vue'),
            meta: {
                requiresAuth: true,
            },
        },
    ],
})

router.beforeEach((to) => {
    const session = getAuthSession()

    if (to.meta.requiresAuth && !session) {
        return {
            name: 'login',
            query: {
                redirect: to.fullPath,
            },
        }
    }

    if (
        to.meta.requiredRole === 'ADMIN' &&
        session?.role !== 'ADMIN'
    ) {
        return {
            name: 'login',
        }
    }
})

export default router
