import { createSlice, PayloadAction } from '@reduxjs/toolkit';

interface SidebarState {
  open: boolean;
}

const sidebarSlice = createSlice({
  name: 'sidebar',
  initialState: { open: true } as SidebarState,
  reducers: {
    toggleSidebar(state) {
      state.open = !state.open;
    },
    setSidebar(state, action: PayloadAction<boolean>) {
      state.open = action.payload;
    },
  },
});

export const { toggleSidebar, setSidebar } = sidebarSlice.actions;
export const selectSidebarOpen = (state: { sidebar: SidebarState }): boolean => state.sidebar.open;
export default sidebarSlice.reducer;
