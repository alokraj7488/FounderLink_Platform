import { configureStore } from '@reduxjs/toolkit';
import authReducer from './slices/authSlice';
import notificationReducer from './slices/notificationSlice';
import themeReducer from './slices/themeSlice';
import startupReducer from './slices/startupSlice';
import sidebarReducer from './slices/sidebarSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    notifications: notificationReducer,
    theme: themeReducer,
    startups: startupReducer,
    sidebar: sidebarReducer,
  },
});

export type AppDispatch = typeof store.dispatch;

export default store;
