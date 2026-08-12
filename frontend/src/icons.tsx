import type { SVGProps } from 'react'

/* eslint-disable react-refresh/only-export-components */

type IconProps = SVGProps<SVGSVGElement>

function Icon({ children, ...props }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      aria-hidden="true"
      {...props}
    >
      {children}
    </svg>
  )
}

export const Icons = {
  dashboard: (props: IconProps) => (
    <Icon {...props}>
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <rect x="14" y="14" width="7" height="7" rx="1" />
    </Icon>
  ),
  services: (props: IconProps) => (
    <Icon {...props}>
      <rect x="3" y="4" width="18" height="6" rx="2" />
      <rect x="3" y="14" width="18" height="6" rx="2" />
      <path d="M7 7h.01M7 17h.01" />
    </Icon>
  ),
  incidents: (props: IconProps) => (
    <Icon {...props}>
      <path d="M12 3 2.8 19h18.4L12 3Z" />
      <path d="M12 9v4M12 16.5h.01" />
    </Icon>
  ),
  analytics: (props: IconProps) => (
    <Icon {...props}>
      <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" />
    </Icon>
  ),
  plus: (props: IconProps) => (
    <Icon {...props}>
      <path d="M12 5v14M5 12h14" />
    </Icon>
  ),
  search: (props: IconProps) => (
    <Icon {...props}>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-4-4" />
    </Icon>
  ),
  refresh: (props: IconProps) => (
    <Icon {...props}>
      <path d="M20 6v5h-5M4 18v-5h5" />
      <path d="M18.5 9A7 7 0 0 0 6 6.5L4 11M5.5 15A7 7 0 0 0 18 17.5l2-4.5" />
    </Icon>
  ),
  chevron: (props: IconProps) => (
    <Icon {...props}>
      <path d="m9 18 6-6-6-6" />
    </Icon>
  ),
  close: (props: IconProps) => (
    <Icon {...props}>
      <path d="M6 6l12 12M18 6 6 18" />
    </Icon>
  ),
  edit: (props: IconProps) => (
    <Icon {...props}>
      <path d="m4 20 4.2-1 10.6-10.6a2 2 0 0 0-2.8-2.8L5.4 16.2 4 20Z" />
    </Icon>
  ),
  trash: (props: IconProps) => (
    <Icon {...props}>
      <path d="M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13" />
    </Icon>
  ),
}
