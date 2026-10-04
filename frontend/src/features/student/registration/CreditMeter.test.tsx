import { render, screen } from '@testing-library/react'
import { CreditMeter } from './CreditMeter'

describe('CreditMeter', () => {
  it('tells the student how many more credits they need', () => {
    render(<CreditMeter total={7} min={12} max={24} />)
    expect(screen.getByText('5 more credits needed (minimum 12)')).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Credit load' })).toHaveAttribute('aria-valuenow', '7')
  })

  it('confirms a load inside the allowed range', () => {
    render(<CreditMeter total={15} min={12} max={24} />)
    expect(screen.getByText('Within the allowed load of 12–24 credits')).toBeInTheDocument()
  })

  it('uses the singular for one missing credit', () => {
    render(<CreditMeter total={11} min={12} max={24} />)
    expect(screen.getByText('1 more credit needed (minimum 12)')).toBeInTheDocument()
  })

  it('flags a load over the maximum', () => {
    render(<CreditMeter total={26} min={12} max={24} />)
    expect(screen.getByText('2 over the maximum of 24')).toBeInTheDocument()
  })
})
