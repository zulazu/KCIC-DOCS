import type { SVGProps } from 'react';

export type IconName =
  | 'home'
  | 'users'
  | 'shield'
  | 'building'
  | 'menu'
  | 'close'
  | 'chevron-left'
  | 'chevron-right'
  | 'chevron-down'
  | 'logout'
  | 'search'
  | 'plus'
  | 'alert'
  | 'check'
  | 'lock'
  | 'bell'
  | 'arrow-left'
  | 'document'
  | 'folder'
  | 'star'
  | 'download'
  | 'eye'
  | 'upload'
  | 'link'
  | 'edit'
  | 'archive'
  | 'restore'
  | 'template';

const paths: Record<IconName, string[]> = {
  home: ['M3 10.5 12 3l9 7.5', 'M5 9.5V21h14V9.5'],
  users: [
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2',
    'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
    'M22 21v-2a4 4 0 0 0-3-3.87',
    'M16 3.13a4 4 0 0 1 0 7.75',
  ],
  shield: ['M12 22s8-3.5 8-10V5l-8-3-8 3v7c0 6.5 8 10 8 10Z', 'm9 12 2 2 4-4'],
  building: [
    'M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16',
    'M16 9h2a2 2 0 0 1 2 2v10',
    'M8 7h4M8 11h4M8 15h4M8 21h8',
    'M3 21h18',
  ],
  menu: ['M4 6h16M4 12h16M4 18h16'],
  close: ['M6 6l12 12M18 6 6 18'],
  'chevron-left': ['m15 18-6-6 6-6'],
  'chevron-right': ['m9 18 6-6-6-6'],
  'chevron-down': ['m6 9 6 6 6-6'],
  logout: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'm16 17 5-5-5-5', 'M21 12H9'],
  search: ['M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16Z', 'm21 21-4.35-4.35'],
  plus: ['M12 5v14M5 12h14'],
  alert: ['M12 9v4M12 17h.01', 'M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z'],
  check: ['m20 6-11 11-5-5'],
  lock: ['M7 11V7a5 5 0 0 1 10 0v4', 'M5 11h14v10H5z'],
  bell: ['M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9', 'M13.7 21a2 2 0 0 1-3.4 0'],
  'arrow-left': ['M19 12H5', 'm12 19-7-7 7-7'],
  document: ['M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z', 'M14 2v6h6', 'M8 13h8M8 17h8'],
  folder: ['M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z'],
  star: ['M12 3l2.7 5.6 6.3.9-4.5 4.4 1.1 6.2L12 17.2 6.4 20l1.1-6.2L3 9.5l6.3-.9Z'],
  download: ['M12 3v12', 'm7 11 5 5 5-5', 'M5 21h14'],
  eye: ['M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z'],
  upload: ['M12 21V9', 'm7 13 5-5 5 5', 'M5 3h14'],
  link: ['M10 13a5 5 0 0 0 7.1 0l2-2a5 5 0 0 0-7.1-7.1L11 5', 'M14 11a5 5 0 0 0-7.1 0l-2 2A5 5 0 0 0 12 20l1-1'],
  edit: ['M12 20h9', 'M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z'],
  archive: ['M3 5h18v4H3z', 'M5 9v11h14V9', 'M10 13h4'],
  restore: ['M3 12a9 9 0 1 0 3-6.7', 'M3 4v5h5'],
  template: ['M4 4h16v16H4Z', 'M4 9h16', 'M9 21V9'],
};

interface IconProps extends SVGProps<SVGSVGElement> {
  name: IconName;
  size?: number;
}

export function Icon({ name, size = 18, ...rest }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {paths[name].map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  );
}
