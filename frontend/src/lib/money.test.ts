import { amountError, balanceStatus, formatMoney, toAmount } from './money'

describe('money helpers', () => {
  it('formats amounts with the currency code and two decimals', () => {
    expect(formatMoney(85000, 'KES')).toBe('KES 85,000.00')
    expect(formatMoney(0.5, 'KES')).toBe('KES 0.50')
    expect(formatMoney(-50, 'KES')).toBe('-KES 50.00')
    expect(formatMoney(null, 'KES')).toBe('—')
  })

  it('validates typed amounts', () => {
    expect(amountError('1500', undefined)).toBeNull()
    expect(amountError('1500.5')).toBeNull()
    expect(amountError('')).toBe('Enter an amount.')
    expect(amountError('15,000')).toBe('Enter an amount such as 1500 or 1500.50.')
    expect(amountError('1.234')).toBe('Enter an amount such as 1500 or 1500.50.')
    expect(amountError('0')).toBe('The amount must be more than zero.')
    expect(amountError('-5')).toBe('Enter an amount such as 1500 or 1500.50.')
    expect(amountError('600', 500)).toBe('At most 500.00.')
  })

  it('sends exact two-decimal strings', () => {
    expect(toAmount('1500')).toBe('1500.00')
    expect(toAmount('1500.5')).toBe('1500.50')
    expect(toAmount(' 0.13 ')).toBe('0.13')
  })

  it('describes a balance as due, in credit or settled', () => {
    expect(balanceStatus(1200)).toMatchObject({ label: 'Balance due', amount: 1200 })
    expect(balanceStatus(-300)).toMatchObject({ label: 'In credit', amount: 300 })
    expect(balanceStatus(0)).toMatchObject({ label: 'Settled', amount: 0 })
  })
})
