import { createRouter, createWebHistory } from 'vue-router'

import AdminBoardCreateView from '@/views/AdminBoardCreateView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/admin/boards/new',
    },
    {
      path: '/admin/boards/new',
      name: 'admin-board-create',
      component: AdminBoardCreateView,
    },
  ],
})

export default router
