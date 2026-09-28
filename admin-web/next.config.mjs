/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  async rewrites() {
    return [
      { source: '/portal/recharge', destination: '/portal' },
      { source: '/portal/wallet', destination: '/portal' },
      { source: '/portal/history', destination: '/portal' },
      { source: '/portal/marketplace', destination: '/portal' },
      { source: '/portal/rentals', destination: '/portal' },
      { source: '/portal/rentals/booking', destination: '/portal' },
      { source: '/portal/bookings', destination: '/portal' },
      { source: '/portal/account', destination: '/portal' },
      { source: '/admin/users', destination: '/admin' },
      { source: '/admin/financial', destination: '/admin' },
      { source: '/admin/vendors', destination: '/admin' },
      { source: '/admin/rental', destination: '/admin' },
      { source: '/admin/commissions', destination: '/admin' },
    ];
  },
};

export default nextConfig;
