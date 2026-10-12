import { GithubOutlined } from '@ant-design/icons';
import { DefaultFooter } from '@ant-design/pro-components';
import { useIntl } from '@umijs/max';
import React from 'react';

const Footer: React.FC = () => {
  const intl = useIntl();
  const defaultMessage = intl.formatMessage({
    id: 'app.copyright.produced',
    defaultMessage: 'Youngs 医馆出品',
  });

  const currentYear = new Date().getFullYear();

  return (
    <DefaultFooter
      style={{
        background: 'none',
      }}
      copyright={`${currentYear} ${defaultMessage}`}
      links={[
        {
          key: 'name',
          title: '泰州最专业养生团队',
          href: 'https://www.youngs.fun',
          blankTarget: true,
        },
        {
          key: 'github',
          title: <GithubOutlined />,
          href: 'https://github.com/cuihairu',
          blankTarget: true,
        },
        {
          key: 'home',
          title: 'www.youngs.fun',
          href: 'https://www.youngs.fun',
          blankTarget: true,
        },
      ]}
    />
  );
};

export default Footer;
