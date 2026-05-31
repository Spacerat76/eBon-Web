import React from 'react'
import { render } from '@testing-library/react'
import Button from '../../components/ui/Button'

describe('Button component', () => {
  it('renders primary variant by default', () => {
    const { getByText } = render(<Button>Click</Button>)
    const btn = getByText('Click')
    expect(btn).toBeInTheDocument()
    // class contains tailwind primary bg
    expect(btn.className).toContain('bg-blue-600')
  })
})
