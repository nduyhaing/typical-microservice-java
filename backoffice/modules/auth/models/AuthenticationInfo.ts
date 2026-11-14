export type AuthenticationInfo = {
  isAuthenticated: boolean;
  authenticatedUser: { username: string } | null;
};
